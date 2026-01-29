package com.leets.commitatobe.domain.commit.service;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
class CommitUpdateServiceTest {

	@Autowired
	private CommitUpdateService commitUpdateService;

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
	}

	@AfterEach
	void tearDown() {
		commitRepository.deleteAll();
		userRepository.deleteAll();
		tierRepository.deleteAll();
	}

	@Test
	@DisplayName("커밋 업데이트 및 경험치 계산 동시성 테스트")
	void testConcurrentUpdateAndCalculate() throws InterruptedException {

		// Given
		LocalDate today = LocalDate.now();

		int numberOfThreads = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
		CountDownLatch latch = new CountDownLatch(numberOfThreads);

		// When - 각 스레드가 독립적인 commitsByDate 맵을 생성하여 사용
		for (int i = 0; i < numberOfThreads; i++) {
			executorService.submit(() -> {
				try {
					Map<LocalDateTime, Integer> commitsByDate = new ConcurrentHashMap<>();
					commitsByDate.put(today.minusDays(2).atStartOfDay(), 5);
					commitsByDate.put(today.minusDays(1).atStartOfDay(), 10);
					commitsByDate.put(today.atStartOfDay(), 15);

					User currentUser = userRepository.findByGithubId(user.getGithubId()).orElseThrow();
					commitUpdateService.updateAndCalculate(currentUser, commitsByDate);
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();
		executorService.shutdown();

		// Then
		User updatedUser = userRepository.findByGithubId(user.getGithubId()).orElseThrow();

		// 경험치 계산 - 락으로 인해 마지막 스레드의 결과만 반영됨
		// commit1: 5*10 + 100 = 150
		// commit2: 10*10 + 100 + 10 = 210
		// commit3: 15*10 + 100 + 20 = 270
		// total: 150 + 210 + 270 = 630
		int expectedExp = 630;

		assertThat(updatedUser.getExp()).isEqualTo(expectedExp);
	}
}
