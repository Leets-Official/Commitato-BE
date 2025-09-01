package com.leets.commitatobe.domain.token.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash(value = "token")
public class GithubTokenRedis {
	@Id
	private String githubId;

	private String accessToken;

	private String refreshToken;
}
