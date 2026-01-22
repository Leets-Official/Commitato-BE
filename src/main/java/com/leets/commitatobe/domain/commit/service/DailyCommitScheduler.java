package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.leets.commitatobe.global.config.redis.annotation.RedissonLock;

import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;
import com.leets.commitatobe.global.executor.LogExecutionTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyCommitScheduler {
	private static final long SIX_MONTHS = 6L;

	private final UserRepository userRepository;
	private final CommitUpdateService commitUpdateService;

	private final ExecutorService executorService = Executors.newFixedThreadPool(10);

	@Scheduled(cron = "0 10 12 * * *", zone = "Asia/Seoul")
	@RedissonLock(key = "'commit-update-scheduler'", leaseTime = 600L)
	@LogExecutionTime
	public void updateAllUsersCommits() {
		List<User> users = userRepository.findAllByIsHumanAccountFalse();
		LocalDateTime afterHalfYear = LocalDateTime.now().minusMonths(SIX_MONTHS);

		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (User user : users) {
			if (user.getLastLoginAt() != null && !user.getLastLoginAt().isAfter(afterHalfYear)) {
				commitUpdateService.processHumanAccount(user.getId()); // 트랜잭션 분리
				continue;
			}

			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				try {
					commitUpdateService.schedulerUpdateUserCommit(user.getId());
				} catch (Exception e) {
					log.error("유저 {} 커밋 업데이트 실패", user.getGithubId(), e);
				}
			}, executorService);

			futures.add(future);
		}

		// 모든 작업이 끝날 때까지 대기
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}
}
