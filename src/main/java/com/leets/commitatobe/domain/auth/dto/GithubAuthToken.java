package com.leets.commitatobe.domain.auth.dto;

public record GithubAuthToken(
	String accessToken,
	String refreshToken
) {
}
