package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.leets.commitatobe.domain.auth.service.AuthService;
import com.leets.commitatobe.domain.commit.domain.Commit;
import com.leets.commitatobe.domain.commit.repository.CommitRepository;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DailyCommitScheduler {
	private final UserRepository userRepository;
	private final GitHubService gitHubService;
	private final CommitRepository commitRepository;
	private final ExpService expService;

	@Scheduled(cron = "0 30 06 * * *")
	@Transactional
	public void updateAllUsersCommits() {
		gitHubService.disableAuth();

		List<User> users = userRepository.findAll();

		for (User user : users) {
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
		}
	}
}
