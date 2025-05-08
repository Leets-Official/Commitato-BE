package com.leets.commitatobe.domain.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.leets.commitatobe.domain.auth.domain.CustomUserDetails;
import com.leets.commitatobe.domain.auth.dto.GitHubDto;
import com.leets.commitatobe.global.exception.ApiException;
import com.leets.commitatobe.global.response.code.status.ErrorStatus;

@Service
public class AuthQueryService {

	public GitHubDto getGitHubUser() {
		CustomUserDetails userDetails = getUserDetails();
		if (userDetails == null) {
			throw new ApiException(ErrorStatus._JWT_NOT_FOUND);
		} else {
			return userDetails.getGitHubDto();
		}
	}

	public String getGitHubId() {
		CustomUserDetails userDetails = getUserDetails();
		if (userDetails == null) {
			return null;
		} else {
			return userDetails.getGithubId();
		}
	}

	private CustomUserDetails getUserDetails() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
			return null;
		}

		return userDetails;
	}
}
