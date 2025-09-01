package com.leets.commitatobe.domain.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.leets.commitatobe.domain.auth.dto.GithubToken;
import com.leets.commitatobe.domain.token.entity.GithubTokenRedis;
import com.leets.commitatobe.domain.token.repository.GithubTokenRedisRepository;
import com.leets.commitatobe.global.exception.ApiException;
import com.leets.commitatobe.global.response.code.status.ErrorStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GithubTokenService {
	private final GithubTokenRedisRepository githubTokenRedisRepository;
	private final AuthService authService;

	public void saveTokens(String githubId, GithubToken token) {
		GithubTokenRedis tokenRedis = GithubTokenRedis.builder()
			.githubId(githubId)
			.accessToken(authService.encrypt(token.accessToken()))
			.refreshToken(authService.encrypt(token.refreshToken()))
			.build();

		githubTokenRedisRepository.save(tokenRedis);
	}

	public GithubToken updateAccessTokenByRefreshToken(String githubId) {
		String refreshToken = getDecryptedRefreshToken(githubId)
			.orElseThrow(() -> new ApiException(ErrorStatus._REFRESH_TOKEN_EXPIRED));

		GithubToken newToken = authService.refreshAccessToken(refreshToken);

		saveTokens(githubId, newToken);

		return newToken;
	}

	public Optional<String> getDecryptedAccessToken(String githubId) {
		return githubTokenRedisRepository.findById(githubId)
			.map(GithubTokenRedis::getAccessToken)
			.map(authService::decrypt);
	}

	public Optional<String> getDecryptedRefreshToken(String githubId) {
		return githubTokenRedisRepository.findById(githubId)
			.map(GithubTokenRedis::getRefreshToken)
			.map(authService::decrypt);
	}
}
