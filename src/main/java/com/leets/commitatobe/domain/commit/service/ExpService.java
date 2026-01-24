package com.leets.commitatobe.domain.commit.service;

import static com.leets.commitatobe.global.response.code.status.ErrorStatus.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

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
import com.leets.commitatobe.global.response.code.status.ErrorStatus;

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

	public void calculateExpAndTier(User user) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime lastUpdate = user.getLastCommitUpdateTime();

		LocalDateTime windowStart = now.minusMonths(2).withDayOfMonth(1).toLocalDate().atStartOfDay();

		List<Commit> windowCommits = commitRepository.findAllByUserAndCommitDateAfter(user,
			windowStart.minusSeconds(1));

		int calculatedWindowExp = calculateExpForCommits(windowCommits, user);
		int calculatedWindowCommitCount = windowCommits.stream().mapToInt(Commit::getCnt).sum();

		LocalDateTime nextDeductibleStart = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
		List<Commit> nextDeductibleCommits = windowCommits.stream()
			.filter(c -> !c.getCommitDate().isBefore(nextDeductibleStart))
			.toList();

		int newNextMonthDeductibleExp = calculateExpForCommits(nextDeductibleCommits, user);
		int newNextMonthDeductibleCommitCount = nextDeductibleCommits.stream().mapToInt(Commit::getCnt).sum();

		int totalExp = user.getExp();
		int totalCommitCount = user.getTotalCommitCount();

		boolean isMonthChanged = lastUpdate != null && !lastUpdate.getMonth().equals(now.getMonth());

		if (lastUpdate == null) {
			totalExp += calculatedWindowExp;
			totalCommitCount += calculatedWindowCommitCount;
		} else if (isMonthChanged) {
			totalExp = totalExp - user.getLastTwoMonthExp() + calculatedWindowExp;
			totalCommitCount = totalCommitCount - user.getLastTwoMonthCommitCount() + calculatedWindowCommitCount;
		} else {
			totalExp = totalExp - user.getCurrentUpdateExp() + calculatedWindowExp;
			totalCommitCount = totalCommitCount - user.getCurrentUpdateCommitCount() + calculatedWindowCommitCount;
		}

		int currentConsecutiveDays = calculateCurrentConsecutiveDays(user);
		user.updateConsecutiveCommitDays(currentConsecutiveDays);

		int todayCommitCount = windowCommits.stream()
			.filter(commit -> commit.getCommitDate().toLocalDate().equals(now.toLocalDate()))
			.mapToInt(Commit::getCnt)
			.findFirst()
			.orElse(0);

		user.updateTodayCommitCount(todayCommitCount);
		user.updateExp(Math.max(0, totalExp));
		user.updateTotalCommitCount(Math.max(0, totalCommitCount));
		user.updateTier(determineTier(totalExp));

		user.updateCalcStats(
			calculatedWindowExp,
			calculatedWindowCommitCount,
			newNextMonthDeductibleExp,
			newNextMonthDeductibleCommitCount
		);
		user.updateLastCommitUpdateTime(now);

		userRepository.save(user);
	}

	private int calculateExpForCommits(List<Commit> commits, User user) {
		if (commits.isEmpty())
			return 0;

		int baseExp = commits.stream()
			.mapToInt(Commit::getCnt)
			.sum() * POINT_PER_COMMIT;

		int bonusExp = calculateWindowBonusExp(commits, user);

		return baseExp + bonusExp;
	}

	private int calculateWindowBonusExp(List<Commit> commits, User user) {
		List<LocalDate> sortedDates = commits.stream()
			.map(c -> c.getCommitDate().toLocalDate())
			.distinct()
			.sorted()
			.toList();

		int totalBonus = 0;
		int currentConsecutiveDays = 0;
		LocalDate lastDate = null;

		for (LocalDate date : sortedDates) {
			if (lastDate == null) {
				currentConsecutiveDays = getInitialConsecutiveDaysForWindow(date, user);
			} else if (date.equals(lastDate.plusDays(1))) {
				currentConsecutiveDays++;
			} else {
				currentConsecutiveDays = 1;
			}

			int dailyBonus = DAILY_BONUS_EXP + (BONUS_EXP_INCREASE * (currentConsecutiveDays - 1));
			totalBonus += dailyBonus;

			lastDate = date;
		}

		return totalBonus;
	}

	//월 시작일이 연속 커밋이 진행중인지 아닌지 판단
	private int getInitialConsecutiveDaysForWindow(LocalDate windowStartDate, User user) {
		LocalDate yesterday = windowStartDate.minusDays(1);

		if (!commitRepository.existsByUserAndCommitDate(user, yesterday.atStartOfDay())) {
			return 1;
		}

		int consecutiveDays = 1;
		LocalDate checkDate = yesterday.minusDays(1);

		while (commitRepository.existsByUserAndCommitDate(user, checkDate.atStartOfDay())) {
			consecutiveDays++;
			checkDate = checkDate.minusDays(1);
		}

		return consecutiveDays + 1;
	}

	private int calculateCurrentConsecutiveDays(User user) {
		LocalDate today = LocalDate.now();

		if (commitRepository.existsByUserAndCommitDate(user, today.atStartOfDay())) {
			return countConsecutiveDaysFrom(user, today);
		}

		LocalDate yesterday = today.minusDays(1);
		if (commitRepository.existsByUserAndCommitDate(user, yesterday.atStartOfDay())) {
			return countConsecutiveDaysFrom(user, yesterday);
		}

		return 0;
	}

	private int countConsecutiveDaysFrom(User user, LocalDate startDate) {
		int consecutiveDays = 0;
		LocalDate checkDate = startDate;

		while (commitRepository.existsByUserAndCommitDate(user, checkDate.atStartOfDay())) {
			consecutiveDays++;
			checkDate = checkDate.minusDays(1);
		}

		return consecutiveDays;
	}

	private Tier determineTier(Integer exp) {
		return tierRepository.findAll()
			.stream()
			.filter(tier -> tier.isValid(exp))
			.max(Comparator.comparing(Tier::getRequiredExp))
			.orElseThrow(() -> new ApiException(ErrorStatus._TIER_NOT_FOUND));
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
