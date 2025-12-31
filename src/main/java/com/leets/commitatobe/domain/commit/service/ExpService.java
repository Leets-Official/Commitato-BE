package com.leets.commitatobe.domain.commit.service;

import static com.leets.commitatobe.global.response.code.status.ErrorStatus.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.leets.commitatobe.domain.auth.service.AuthQueryService;
import com.leets.commitatobe.domain.commit.domain.Commit;
import com.leets.commitatobe.domain.commit.dto.response.ExpAndTierResponse;
import com.leets.commitatobe.domain.commit.repository.CommitRepository;
import com.leets.commitatobe.domain.tier.domain.Tier;
import com.leets.commitatobe.domain.tier.repository.TierRepository;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;
import com.leets.commitatobe.global.config.redis.annotation.RedissonLock;
import com.leets.commitatobe.global.exception.ApiException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpService {
	private final CommitRepository commitRepository;
	private final UserRepository userRepository;
	private final TierRepository tierRepository;
	private final AuthQueryService authQueryService;

	private static final int POINT_PER_COMMIT = 10;
	private static final int DAILY_BONUS_EXP = 100;
	private static final int BONUS_EXP_INCREASE = 10;

	@RedissonLock(key = "#githubId")
	public void calculateExpAndTier(String githubId) {
		User user = userRepository.findByGithubId(githubId)
			.orElseThrow(() -> new UsernameNotFoundException("해당하는 깃허브 닉네임과 일치하는 유저를 찾을 수 없음: " + githubId));

		int updatedConsecutiveDays = updateConsecutiveDays(user);

		List<Commit> uncalculatedCommits = commitRepository.findAllByUserAndCalculatedFalse(user);
		int baseExp = calculateBaseExp(uncalculatedCommits);
		int bonusExp = calculateTodayBonusExp(user, updatedConsecutiveDays);
		int totalExp = user.getExp() + baseExp + bonusExp;

		int todayCommitCount = todayCommitCount(user);
		int totalCommitCount = totalCommitCount(user);

		user.updateExp(totalExp);
		user.updateTier(determineTier(totalExp));
		user.updateConsecutiveCommitDays(updatedConsecutiveDays);
		user.updateTodayCommitCount(todayCommitCount);
		user.updateTotalCommitCount(totalCommitCount);
		user.updateLastCommitUpdateTime(LocalDateTime.now());

		markCalculated(uncalculatedCommits);

		userRepository.save(user);
	}

	private int updateConsecutiveDays(User user) {
		LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
		LocalDateTime yesterday = today.minusDays(1);

		boolean hasTodayCommit = commitRepository.existsByUserAndCommitDate(user, today);
		boolean hasYesterdayCommit = commitRepository.existsByUserAndCommitDate(user, yesterday);
		if (!hasTodayCommit && !hasYesterdayCommit) {
			return 0;
		}

		int currentConsecutiveDays = user.getConsecutiveCommitDays();

		LocalDate lastCommitDay = commitRepository.findTopByUserAndCalculatedTrueOrderByCommitDateDesc(user)
			.map(commitDate -> commitDate.getCommitDate().toLocalDate())
			.orElse(null);

		if (lastCommitDay == null) {
			return 1;
		}

		LocalDate recentCommitDay = commitRepository.findTopByUserOrderByCommitDateDesc(user)
			.map(commitDate -> commitDate.getCommitDate().toLocalDate())
			.orElse(null);

		if (recentCommitDay != null && recentCommitDay.equals(lastCommitDay)) {
			return currentConsecutiveDays;
		}

		if (recentCommitDay != null && recentCommitDay.equals(lastCommitDay.plusDays(1))) {
			return currentConsecutiveDays + 1;
		}

		return 1;
	}

	private int calculateBaseExp(List<Commit> uncalculatedCommits) {
		int commitCounts = uncalculatedCommits.stream()
			.mapToInt(Commit::getCnt)
			.sum();

		return commitCounts * POINT_PER_COMMIT;
	}

	private int calculateTodayBonusExp(User user, int consecutiveDays) {
		LocalDateTime today = LocalDate.now().atStartOfDay();

		boolean alreadyGotBonusExpToday = commitRepository.existsByUserAndCommitDateAndCalculatedTrue(user, today);
		if (alreadyGotBonusExpToday) {
			return 0;
		}

		boolean hasNewCommitToday = commitRepository.existsByUserAndCommitDate(user, today);
		if (!hasNewCommitToday) {
			return 0;
		}

		return DAILY_BONUS_EXP + (BONUS_EXP_INCREASE * (consecutiveDays - 1));
	}

	private int todayCommitCount(User user) {
		LocalDateTime today = LocalDate.now().atStartOfDay();

		return commitRepository.findAllByUserAndCommitDate(user, today).stream()
			.mapToInt(Commit::getCnt)
			.sum();
	}

	private int totalCommitCount(User user) {
		int currentTotalCommitCount = user.getTotalCommitCount();
		int newCommitCount = commitRepository.findAllByUserAndCalculatedFalse(user).stream()
			.mapToInt(Commit::getCnt)
			.sum();

		return currentTotalCommitCount + newCommitCount;
	}

	private void markCalculated(List<Commit> uncalculatedCommits) {
		uncalculatedCommits.forEach(Commit::markAsCalculated);
	}

	private Tier determineTier(Integer exp) {
		return tierRepository.findAll()
			.stream()
			.filter(tier -> tier.isValid(exp))
			.max(Comparator.comparing(Tier::getRequiredExp))
			.orElseThrow(() -> new ApiException(_TIER_NOT_FOUND));
	}

	public ExpAndTierResponse updateExpAndTier(int exp) {
		String gitHubId = authQueryService.getGitHubId();
		User user = userRepository.findByGithubId(gitHubId)
			.orElseThrow(() -> new ApiException(_USER_NOT_FOUND));

		user.updateExp(exp);
		Tier tier = determineTier(exp);
		user.updateTier(tier);

		return ExpAndTierResponse.from(user);
	}
}
