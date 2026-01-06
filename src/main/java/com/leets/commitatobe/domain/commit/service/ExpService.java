package com.leets.commitatobe.domain.commit.service;

import static com.leets.commitatobe.global.response.code.status.ErrorStatus.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

		Set<LocalDate> commitDateSet = commitRepository.findAllCommitDatesByUser(user).stream()
			.map(LocalDateTime::toLocalDate)
			.collect(Collectors.toSet());

		int updatedConsecutiveDays = updateConsecutiveDays(user, commitDateSet);

		List<Commit> uncalculatedCommits = commitRepository.findAllByUserAndCalculatedFalseOrderByCommitDateAsc(user);
		int baseExp = calculateBaseExp(uncalculatedCommits);
		int bonusExp = calculateTodayBonusExp(uncalculatedCommits, commitDateSet);
		int totalExp = user.getExp() + baseExp + bonusExp;

		int todayCommitCount = todayCommitCount(user);
		int totalCommitCount = totalCommitCount(user, uncalculatedCommits);

		user.updateExp(totalExp);
		user.updateTier(determineTier(totalExp));
		user.updateConsecutiveCommitDays(updatedConsecutiveDays);
		user.updateTodayCommitCount(todayCommitCount);
		user.updateTotalCommitCount(totalCommitCount);
		user.updateLastCommitUpdateTime(LocalDateTime.now());

		markCalculated(uncalculatedCommits);

		userRepository.save(user);
	}

	private int updateConsecutiveDays(User user, Set<LocalDate> commitDateSet) {
		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

		boolean hasTodayCommit = commitDateSet.contains(today);
		boolean hasYesterdayCommit = commitDateSet.contains(yesterday);

		if (!hasTodayCommit && !hasYesterdayCommit) {
			user.updateLastCommitDateAppliedDate(null);
			return 0;
		}

		LocalDate recentCommitDay = hasTodayCommit ? today : yesterday;

		int currentConsecutiveDays = user.getConsecutiveCommitDays();
		LocalDate lastCommitDateAppliedDate = user.getLastCommitDateAppliedDate();

		if (lastCommitDateAppliedDate == null || currentConsecutiveDays == 0) {
			int newConsecutiveDays = 0;
			LocalDate checkDate = recentCommitDay;

			while (commitDateSet.contains(checkDate)) {
				newConsecutiveDays++;
				checkDate = checkDate.minusDays(1);
			}
			user.updateLastCommitDateAppliedDate(recentCommitDay);
			return newConsecutiveDays;
		}

		if (recentCommitDay.isEqual(lastCommitDateAppliedDate)) {
			return currentConsecutiveDays;
		}

		if (recentCommitDay.isEqual(lastCommitDateAppliedDate.plusDays(1))) {
			user.updateLastCommitDateAppliedDate(recentCommitDay);
			return currentConsecutiveDays + 1;
		}

		int newConsecutiveDays = 0;
		LocalDate checkDate = recentCommitDay;
		while (commitDateSet.contains(checkDate)) {
			newConsecutiveDays++;
			checkDate = checkDate.minusDays(1);
		}
		user.updateLastCommitDateAppliedDate(recentCommitDay);
		return newConsecutiveDays;
	}

	private int calculateBaseExp(List<Commit> uncalculatedCommits) {
		int commitCounts = uncalculatedCommits.stream()
			.mapToInt(Commit::uncalculatedDelta)
			.sum();

		return commitCounts * POINT_PER_COMMIT;
	}

	private int calculateTodayBonusExp(List<Commit> uncalculatedCommits, Set<LocalDate> commitDateSet) {
		int totalBonus = 0;

		for (Commit commit : uncalculatedCommits) {
			if (commit.isBonusAwarded()) {
				continue;
			}

			LocalDate commitDate = commit.getCommitDate().toLocalDate();

			int currentConsecutiveDays = getConsecutiveDaysForDate(commitDate, commitDateSet);

			int dailyBonus = DAILY_BONUS_EXP + (BONUS_EXP_INCREASE * (currentConsecutiveDays - 1));
			totalBonus += dailyBonus;

			commit.todayBonusAwarded();
		}

		return totalBonus;
	}

	private int getConsecutiveDaysForDate(LocalDate targetDate, Set<LocalDate> commitDateSet) {
		int consecutiveDays = 1;
		LocalDate checkDate = targetDate.minusDays(1);

		while (commitDateSet.contains(checkDate)) {
			consecutiveDays++;
			checkDate = checkDate.minusDays(1);
		}

		return consecutiveDays;
	}

	private int todayCommitCount(User user) {
		LocalDateTime today = LocalDate.now().atStartOfDay();
		return commitRepository.findAllByUserAndCommitDate(user, today).stream()
			.mapToInt(Commit::getCnt)
			.sum();
	}

	private int totalCommitCount(User user, List<Commit> newCommits) {
		int newCount = newCommits.stream().mapToInt(Commit::uncalculatedDelta).sum();
		return user.getTotalCommitCount() + newCount;
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
