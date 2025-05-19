package com.leets.commitatobe.domain.user.dto.response;

import com.leets.commitatobe.domain.user.domain.User;

import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record UserHoverInfoResponse(
	String githubProfileImage,
	String githubUsername,
	String githubId
) {
	public static UserHoverInfoResponse from(User user) {
		return UserHoverInfoResponse.builder()
			.githubProfileImage(user.getProfileImage())
			.githubUsername(user.getUsername())
			.githubId(user.getGithubId())
			.build();
	}
}
