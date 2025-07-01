package com.leets.commitatobe.domain.user.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

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
