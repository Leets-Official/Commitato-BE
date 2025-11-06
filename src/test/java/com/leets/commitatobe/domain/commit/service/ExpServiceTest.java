package com.leets.commitatobe.domain.commit.service;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.leets.commitatobe.domain.commit.domain.Commit;
import com.leets.commitatobe.domain.commit.repository.CommitRepository;
import com.leets.commitatobe.domain.tier.domain.Tier;
import com.leets.commitatobe.domain.tier.repository.TierRepository;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;
import com.leets.commitatobe.global.config.TestContainerConfig;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@ContextConfiguration(classes = TestContainerConfig.class)
class ExpServiceTest {

	@Autowired
	private ExpService expService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CommitRepository commitRepository;

	@Autowired
	private TierRepository tierRepository;

	private User user;

	@BeforeEach
	void setUp() {
		user = User.builder().username("testuser").githubId("testuser").build();
		userRepository.save(user);

		try {
			Tier tier1 = Tier.class.getDeclaredConstructor().newInstance();
			Tier tier2 = Tier.class.getDeclaredConstructor().newInstance();

			Field tierName = Tier.class.getDeclaredField("tierName");
			tierName.setAccessible(true);
			tierName.set(tier1, "Bronze");
			tierName.set(tier2, "Silver");

			Field characterUrl = Tier.class.getDeclaredField("characterUrl");
			characterUrl.setAccessible(true);
			characterUrl.set(tier1, "url1");
			characterUrl.set(tier2, "url2");

			Field requiredExp = Tier.class.getDeclaredField("requiredExp");
			requiredExp.setAccessible(true);
			requiredExp.set(tier1, 0);
			requiredExp.set(tier2, 1000);

			tierRepository.save(tier1);
			tierRepository.save(tier2);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Commit commit1 = Commit.create(LocalDateTime.now().minusDays(2), 5, user);
		Commit commit2 = Commit.create(LocalDateTime.now().minusDays(1), 10, user);
		Commit commit3 = Commit.create(LocalDateTime.now(), 15, user);
		commitRepository.save(commit1);
		commitRepository.save(commit2);
		commitRepository.save(commit3);
	}

	@AfterEach
	void tearDown() {
		commitRepository.deleteAll();
		userRepository.deleteAll();
		tierRepository.deleteAll();
	}

	@Test
	@DisplayName("경험치 계산 동시성 테스트")
	void testConcurrentExpCalculation() throws InterruptedException {

		// Given
		int numberOfThreads = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
		CountDownLatch latch = new CountDownLatch(numberOfThreads);

		// When
		for (int i = 0; i < numberOfThreads; i++) {
			executorService.submit(() -> {
				try {
					expService.calculateAndSaveExp(user.getGithubId());
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();
		executorService.shutdown();

		// Then
		User updatedUser = userRepository.findByGithubId(user.getGithubId()).orElseThrow();

		// 경험치 계산
		// commit1: 5*5 + 100 = 125
		// commit2: 10*5 + 100 + 10 = 160
		// commit3: 15*5 + 100 + 20 = 195
		// total: 125 + 160 + 195 = 480
		int expectedExp = 480;

		assertThat(updatedUser.getExp()).isEqualTo(expectedExp);
	}
}
