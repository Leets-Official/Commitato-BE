package com.leets.commitatobe.global.exception;

public class GithubUnauthorizedException extends RuntimeException {
	public GithubUnauthorizedException() {
		super("Github 401 Unauthorized");
	}
}
