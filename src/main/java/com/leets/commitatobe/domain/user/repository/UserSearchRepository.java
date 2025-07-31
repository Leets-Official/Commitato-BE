package com.leets.commitatobe.domain.user.repository;

import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.leets.commitatobe.domain.user.domain.UserDocument;

public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {
	List<UserDocument> findByGithubIdStartingWith(String githubId);

	List<UserDocument> findByGithubIdContaining(String githubId);
}
