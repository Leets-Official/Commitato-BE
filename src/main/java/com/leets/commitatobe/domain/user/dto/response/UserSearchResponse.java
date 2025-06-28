package com.leets.commitatobe.domain.user.dto.response;

import com.leets.commitatobe.domain.user.domain.User;

public record UserSearchResponse(
	String id,
	String githubId,
	String tierName,
	Integer exp,
	Integer consecutiveCommitDays
) {
	public static UserSearchResponse from(User user) {
		String tierName = user.getTier() != null ? user.getTier().getTierName() : "Unranked";

		return new UserSearchResponse(
			user.getId().toString(),
			user.getGithubId(),
			tierName,
			user.getExp(),
			user.getConsecutiveCommitDays()
		);
	}
}
