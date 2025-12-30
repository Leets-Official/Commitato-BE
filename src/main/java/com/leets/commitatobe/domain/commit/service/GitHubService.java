package com.leets.commitatobe.domain.commit.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leets.commitatobe.global.exception.ApiException;
import com.leets.commitatobe.global.response.code.status.ErrorStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Getter
public class GitHubService {
	private final String GITHUB_API_URL = "https://api.github.com";
	private final WebClient webClient = WebClient.builder()
		.baseUrl(GITHUB_API_URL)
		.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
		.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
		.exchangeStrategies(ExchangeStrategies.builder()
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
			.build())
		.build();

	// GitHub repository 이름 저장
	public List<String> fetchRepos(String accessToken, String gitHubUsername) {
		Set<String> repoFullNames = new HashSet<>();

		JsonArray repos = getConnection("/user/repos?type=all&sort=pushed&per_page=100", accessToken);
		if (repos == null) {
			return new ArrayList<>();
		}

		repos.forEach(repo -> {
			String fullName = repo.getAsJsonObject().get("full_name").getAsString();
			repoFullNames.add(fullName);
		});

		return new ForkJoinPool(Runtime.getRuntime().availableProcessors()).submit(() ->
			repoFullNames.parallelStream()
				.filter(fullName -> isContributor(accessToken, fullName, gitHubUsername))
				.toList()
		).join();
	}

	// commit을 일별로 정리
	public void countCommits(String accessToken, String fullName, String gitHubUsername, LocalDateTime date,
		Map<LocalDateTime, Integer> commitsByDate) {
		int page = 1;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		while (true) {
			JsonArray commits;

			try {
				commits = getConnection(
					"/repos/" + fullName + "/commits?page=" + page + "&per_page=100" + "&since=" + formatToISO8601(
						date), accessToken);
			} catch (Exception e) {
				return;
			}

			if (commits == null || commits.isEmpty()) {
				return;
			}

			for (int i = 0; i < commits.size(); i++) {
				JsonObject commit = commits.get(i).getAsJsonObject();

				if (!validateAuthor(commit, gitHubUsername)) {
					continue;
				}

				String commitDateTime = getCommitDateTime(commit);
				if (commitDateTime.length() < 10) {
					continue;
				}

				LocalDateTime commitDate = LocalDate.parse(commitDateTime.substring(0, 10), formatter).atStartOfDay();
				commitsByDate.merge(commitDate, 1, Integer::sum);
			}

			page++;
		}
	}

	// http 연결
	private JsonArray getConnection(String url, String accessToken) {
		return webClient.get()
			.uri(url)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.retrieve()
			.onStatus(s -> s.value() == 401,
				r -> Mono.error(new ApiException(ErrorStatus._UNAUTHORIZED)))
			.bodyToMono(String.class)
			.map(res -> JsonParser.parseString(res).getAsJsonArray())
			.block();
	}

	// 자신이 해당 repository의 기여자 인지 확인
	private boolean isContributor(String accessToken, String fullName, String gitHubUsername) {
		if (fullName.contains(gitHubUsername)) {
			return true;
		}

		JsonArray contributors = getConnection("/repos/" + fullName + "/contributors", accessToken);

		if (contributors == null) {
			return false;
		}

		for (int i = 0; i < contributors.size(); i++) {
			JsonObject contributor = contributors.get(i).getAsJsonObject();

			if (contributor.has("login") && !contributor.get("login").isJsonNull()) {
				String contributorLogin = contributor.get("login").getAsString();

				if (contributorLogin.equals(gitHubUsername)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean validateAuthor(JsonObject commitJson, String gitHubUsername) {
		if (commitJson.has("author") && !commitJson.get("author").isJsonNull()) {
			JsonElement topAuthor = commitJson.getAsJsonObject("author").get("login");
			if (topAuthor != null && !topAuthor.isJsonNull()) {
				return topAuthor.getAsString().equals(gitHubUsername);
			}
		}
		// author가 null이면 해당 커밋을 스킵
		return false;
	}

	// commit 시간 추출
	private String getCommitDateTime(JsonObject commit) {
		String originCommitDateTime = commit.get("commit").getAsJsonObject() // UTC+0
			.get("author").getAsJsonObject()
			.get("date").getAsString();
		Instant instant = Instant.parse(originCommitDateTime);

		ZoneId kst = ZoneId.of("Asia/Seoul");
		return instant.atZone(kst).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	// GitHub API에서 제공하는 시간 표현법으로 변환
	private String formatToISO8601(LocalDateTime dateTime) {
		ZoneId kst = ZoneId.of("Asia/Seoul");
		Instant instant = dateTime.atZone(kst).toInstant();

		return DateTimeFormatter.ISO_INSTANT.format(instant);
	}
}
