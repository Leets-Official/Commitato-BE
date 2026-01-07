package com.leets.commitatobe.domain.commit.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.global.shared.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "commit")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Commit extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "commit_id")
	private UUID id;

	@Column
	private Integer cnt;

	@Column
	private LocalDateTime commitDate;

	@ManyToOne
	@JoinColumn(name = "user_id")
	@JsonBackReference
	private User user;

	public static Commit create(LocalDateTime commitDate, Integer cnt, User user) {
		return Commit.builder()
			.commitDate(commitDate)
			.cnt(cnt)
			.user(user)
			.build();
	}

	public void updateCnt(Integer newCnt) {
		this.cnt = newCnt;
	}
}
