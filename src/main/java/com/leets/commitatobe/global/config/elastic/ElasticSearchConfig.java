package com.leets.commitatobe.global.config.elastic;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.http.impl.client.BasicCredentialsProvider;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@Configuration
public class ElasticSearchConfig {

	@Value("${spring.elasticsearch.username}")
	private String username;

	@Value("${spring.elasticsearch.password}")
	private String password;

	@Bean
	public ElasticsearchClient elasticSearchClient() {
		SSLContext sslContext;
		try {
			sslContext = SSLContextBuilder.create()
				.loadTrustMaterial(null, (certificate, authType) -> true)
				.build();
		} catch (Exception e) {
			throw new RuntimeException("SSLContext 구성 실패", e);
		}

		BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

		RestClient restClient = RestClient.builder(
				new HttpHost("localhost", 9200, "https"))
			.setHttpClientConfigCallback(httpClientBuilder ->
				httpClientBuilder.setSSLContext(sslContext)
					.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
					.setDefaultCredentialsProvider(credentialsProvider)
			).build();

		RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
		return new ElasticsearchClient(transport);
	}
}
