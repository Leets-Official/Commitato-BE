package com.leets.commitatobe.domain.user.service;

/*@Component
@RequiredArgsConstructor
public class RankingScheduler {
	private final UserRepository userRepository;

	@Scheduled(cron = "0 30 0/3 * * *")
	@Transactional
	public void updateUserRankings() {
		List<User> allUsers = userRepository.findAllByOrderByExpDesc(Pageable.unpaged()).getContent();

		int ranking = 0;
		int previousExp = -1;

		for (User user : allUsers) {
			if (!user.getExp().equals(previousExp)) {
				ranking++;
				previousExp = user.getExp();
			}
			user.updateRank(ranking);
			userRepository.save(user);
		}
	}
}
*/