package com.leets.commitatobe.domain.auth.dto;

public record GithubToken(
	String accessToken,
	String refreshToken
) {
}
