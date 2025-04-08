package com.leets.commitatobe.domain.commit.dto.response;

import com.leets.commitatobe.domain.user.domain.User;

public record ExpAndTierResponse(
	String id,
	String githubId,
	String tierName,
	Integer exp
) {
	public static ExpAndTierResponse from(User user) {
		return new ExpAndTierResponse(
			user.getId().toString(),
			user.getGithubId(),
			user.getTier().getTierName(),
			user.getExp()
		);
	}
}
