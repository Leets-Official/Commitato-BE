package com.leets.commitatobe.domain.auth.dto;

import com.leets.commitatobe.global.jwt.dto.JwtResponse;

public record LoginResponse(
	boolean isNewUser,
	JwtResponse jwtResponse
) {
	public static LoginResponse of(boolean isNewUser, JwtResponse jwtResponse) {
		return new LoginResponse(isNewUser, jwtResponse);
	}
}
