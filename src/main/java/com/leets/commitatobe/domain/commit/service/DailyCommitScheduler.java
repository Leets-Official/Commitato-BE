package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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

	@Scheduled(cron = "0 30 06 * * *")
	@Transactional
	public void updateAllUsersCommits() {
		List<User> users = userRepository.findAllByIsHumanAccountFalse();

		LocalDateTime afterHalfYear = LocalDateTime.now().minusMonths(SIX_MONTHS);

		for (User user : users) {
			if (user.getLastLoginAt() != null && !user.getLastLoginAt().isAfter(afterHalfYear)) {
				user.changeHumanAccount();
				userRepository.save(user);
				continue;
			}
			try {
				tryCommitUpdate(user);
			} catch (RuntimeException e) {
				GithubToken newToken = githubTokenService.updateAccessTokenByRefreshToken(user.getGithubId());
				gitHubService.updateToken(newToken.accessToken());

				tryCommitUpdate(user);
			}
		}
	}

	private void tryCommitUpdate(User user) {
		gitHubService.runWithoutRedirect(() -> {
			String token = githubTokenService.getDecryptedAccessToken(user.getGithubId())
				.orElseThrow(() -> new ApiException(ErrorStatus._UNAUTHORIZED));
			gitHubService.updateToken(token);

			LocalDateTime time = user.getLastCommitUpdateTime();
			if (time == null) {
				time = user.getCreatedAt().toLocalDate().atStartOfDay();
			}

			LocalDateTime since = time.minusHours(9);
			gitHubService.fetchRepos(user.getGithubId())
				.forEach(name ->
					gitHubService.countCommits(name, user.getGithubId(), since));

			gitHubService.getCommitsByDate().forEach((date, cnt) -> {
					Commit commit = commitRepository
						.findByCommitDateAndUser(date, user)
						.orElse(Commit.create(date, 0, user));
					commit.updateCnt(commit.getCnt() + cnt);
					commitRepository.save(commit);
				}
			);

			user.updateLastCommitUpdateTime(LocalDateTime.now());
			userRepository.save(user);

			expService.calculateAndSaveExp(user.getGithubId());
		});
	}
}
