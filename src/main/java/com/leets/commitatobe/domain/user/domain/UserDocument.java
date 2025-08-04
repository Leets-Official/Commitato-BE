package com.leets.commitatobe.domain.user.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(indexName = "users")
@Setting(settingPath = "elasticsearch/settings.json")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {
	@Id
	private String id;

	@Field(type = FieldType.Text, analyzer = "hyphen_preserving")
	private String githubId;

	private String tierName;

	private Integer ranking;

	private Integer exp;

	private Integer consecutiveCommitDays;
}
