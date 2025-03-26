package com.leets.commitatobe.domain.user.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(indexName = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {
	@Id
	private String id;
	private String githubId;

	private String tierName;
	private Integer ranking;
	private Integer exp;
	private Integer consecutiveCommitDays;


}
