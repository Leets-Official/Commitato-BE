package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.leets.commitatobe.global.config.redis.annotation.RedissonLock;

import org.springframework.transaction.annotation.Transactional;

import com.leets.commitatobe.domain.auth.dto.GithubToken;
import com.leets.commitatobe.domain.auth.service.GithubTokenService;
import com.leets.commitatobe.domain.commit.domain.Commit;
import com.leets.commitatobe.domain.commit.repository.CommitRepository;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;
import com.leets.commitatobe.global.exception.ApiException;
import com.leets.commitatobe.global.response.code.status.ErrorStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DailyCommitScheduler {
	private static final long SIX_MONTHS = 6L;

	private final UserRepository userRepository;
	private final GitHubService gitHubService;
	private final CommitRepository commitRepository;
	private final ExpService expService;
	private final GithubTokenService githubTokenService;

	@Scheduled(cron = "0 14 21 * * *", zone = "Asia/Seoul")
	@Transactional
	@RedissonLock(key = "'commit-update-scheduler'", leaseTime = 600L)
	public void updateAllUsersCommits() {
		List<User> users = userRepository.findAllByIsHumanAccountFalse();

		LocalDateTime afterHalfYear = LocalDateTime.now().minusMonths(SIX_MONTHS);

		for (User user : users) {
			if (user.getLastLoginAt() != null && !user.getLastLoginAt().isAfter(afterHalfYear)) {
				user.changeHumanAccount();
				userRepository.save(user);
				continue;
			}

			String accessToken = githubTokenService.getDecryptedAccessToken(user.getGithubId())
				.orElseThrow(() -> new ApiException(ErrorStatus._UNAUTHORIZED));

			try {
				tryCommitUpdate(user, accessToken);
			} catch (ApiException e) {
				if (e.getErrorReasonHttpStatus().getHttpStatus() != HttpStatus.UNAUTHORIZED) {
					throw e;
				}

				GithubToken newToken = githubTokenService.updateAccessTokenByRefreshToken(user.getGithubId());
				tryCommitUpdate(user, newToken.accessToken());
			}
		}
	}

	private void tryCommitUpdate(User user, String accessToken) {
		LocalDateTime time = user.getLastCommitUpdateTime();
		if (time == null) {
			time = user.getCreatedAt().toLocalDate().atStartOfDay();
		}
		LocalDateTime since = time;

		Map<LocalDateTime, Integer> commitsByDate = new ConcurrentHashMap<>();

		gitHubService.fetchRepos(accessToken)
			.forEach(name ->
				gitHubService.countCommits(accessToken, name, user.getGithubId(), since, commitsByDate));

		commitsByDate.forEach((date, delta) -> {
			LocalDateTime day = date.toLocalDate().atStartOfDay();
			Commit commit = commitRepository.findByUserAndCommitDate(user, day)
				.orElseGet(() -> Commit.create(day, 0, user));

			commit.addCnt(delta);
			commitRepository.save(commit);
		});

		expService.calculateExpAndTier(user.getGithubId());
	}
}
