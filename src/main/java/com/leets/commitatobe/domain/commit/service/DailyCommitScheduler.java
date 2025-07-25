package com.leets.commitatobe.domain.commit.service;

/*@Component
@RequiredArgsConstructor
public class DailyCommitScheduler {
	private final UserRepository userRepository;
	private final GitHubService gitHubService;
	private final CommitRepository commitRepository;
	private final ExpService expService;
	private final AuthService authService;

	@Scheduled(cron = "0 05 00 * * *")
	@Transactional
	public void updateAllUsersCommits() {
		List<User> users = userRepository.findAll();

		for (User user : users) {
			String token = authService.decrypt(user.getGitHubAccessToken());
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
						.orElse(Commit.create(date, cnt, user));
					commit.updateCnt(cnt);
					commitRepository.save(commit);
				}
			);

			user.updateLastCommitUpdateTime(LocalDateTime.now());
			userRepository.save(user);

			expService.calculateAndSaveExp(user.getGithubId());
		}
	}
}
*/