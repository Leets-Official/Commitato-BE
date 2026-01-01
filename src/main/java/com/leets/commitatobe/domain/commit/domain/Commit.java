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

	@Column(name = "is_calculated")
	private boolean calculated;//경험치 계산 여부를 나타낸다.

	@Column(name = "calculated_count", nullable = false)
	@Builder.Default
	private Integer calculatedCount = 0;

	@Column(name = "bonus_awarded", nullable = false)
	@Builder.Default
	private boolean bonusAwarded = false;

	public static Commit create(LocalDateTime commitDate, Integer cnt, User user) {
		return Commit.builder()
			.commitDate(commitDate)
			.cnt(cnt)
			.user(user)
			.build();
	}

	public void addCnt(int newCount) {
		if (newCount <= 0) return;
		this.cnt += newCount;
		this.calculated = false; // 다시 계산 필요(단, delta만 계산할 거라 안전)
	}

	public int uncalculatedDelta() {
		return Math.max(0, this.cnt - this.calculatedCount);
	}

	public void markAsCalculated() {
		this.calculatedCount = this.cnt;
		this.calculated = true;
	}

	public void todayBonusAwarded() {
		this.bonusAwarded = true;
	}
}
