package com.leets.commitatobe.domain.token.repository;

import org.springframework.data.repository.CrudRepository;

import com.leets.commitatobe.domain.token.entity.GithubTokenRedis;

public interface GithubTokenRedisRepository extends CrudRepository<GithubTokenRedis, String> {
}
