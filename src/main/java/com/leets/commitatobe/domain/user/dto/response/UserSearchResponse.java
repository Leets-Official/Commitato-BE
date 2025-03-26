package com.leets.commitatobe.domain.user.dto.response;

import com.leets.commitatobe.domain.user.domain.User;

public record UserSearchResponse(
	String id,
	String githubId,
	Integer ranking,
	String tierName,
	Integer exp,
	Integer consecutiveCommitDays
) {
	public static UserSearchResponse from(User user) {
		return new UserSearchResponse(
			user.getId().toString(),
			user.getGithubId(),
			user.getRanking(),
			user.getTier().getTierName(),
			user.getExp(),
			user.getConsecutiveCommitDays()
		);
	}
}
