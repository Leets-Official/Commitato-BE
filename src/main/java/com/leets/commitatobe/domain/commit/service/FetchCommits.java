package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.leets.commitatobe.domain.auth.service.AuthQueryService;
import com.leets.commitatobe.domain.commit.dto.response.CommitResponse;
import com.leets.commitatobe.domain.commit.repository.CommitRepository;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;
import com.leets.commitatobe.domain.user.service.UserQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FetchCommits {
	private final CommitRepository commitRepository;
	private final UserRepository userRepository;
	private final GitHubService gitHubService; // GitHub API 통신
	private final AuthQueryService authQueryService;
	private final CommitUpdateService commitUpdateService;
	private final UserQueryService userQueryService;

	public CommitResponse execute() {
		String gitHubId = authQueryService.getGitHubId();
		User user = userRepository.findByGithubId(gitHubId)
			.orElseThrow(() -> new UsernameNotFoundException("해당하는 깃허브 닉네임과 일치하는 유저를 찾을 수 없음: " + gitHubId));

		LocalDateTime since = LocalDate.now().minusMonths(2).withDayOfMonth(1).atStartOfDay();

		try {
			String accessToken = userQueryService.getUserGitHubAccessToken(gitHubId);
			List<String> repos = gitHubService.fetchRepos(accessToken);

			ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			List<CompletableFuture<Void>> futures = new ArrayList<>();
			Map<LocalDateTime, Integer> commitsByDate = new ConcurrentHashMap<>();

			for (String fullName : repos) {
				CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
					gitHubService.countCommits(accessToken, fullName, gitHubId, since, commitsByDate), executor);
				futures.add(future);
			}

			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.join();
			executor.shutdown();

			commitUpdateService.updateAndCalculate(user, commitsByDate);

			User updatedUser = userRepository.findByGithubId(gitHubId)
				.orElseThrow(() -> new UsernameNotFoundException("해당하는 깃허브 닉네임과 일치하는 유저를 찾을 수 없음: " + gitHubId));

			return CommitResponse.of(true, updatedUser);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
