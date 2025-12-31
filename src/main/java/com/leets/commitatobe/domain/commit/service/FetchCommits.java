package com.leets.commitatobe.domain.commit.service;

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
import com.leets.commitatobe.domain.commit.domain.Commit;
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
	private final ExpService expService;
	private final UserQueryService userQueryService;

	public CommitResponse execute() {
		String gitHubId = authQueryService.getGitHubId();
		User user = userRepository.findByGithubId(gitHubId)
			.orElseThrow(() -> new UsernameNotFoundException("해당하는 깃허브 닉네임과 일치하는 유저를 찾을 수 없음: " + gitHubId));

		LocalDateTime calculatedCursor = commitRepository.findTopByUserAndCalculatedIsTrueOrderByUpdatedAtDesc(user)
			.map(Commit::getUpdatedAt)
			.orElseGet(() -> user.getCreatedAt().toLocalDate().atStartOfDay());
		LocalDateTime lastCommitUpdateTime = user.getLastCommitUpdateTime();

		LocalDateTime since = (lastCommitUpdateTime == null)
			? calculatedCursor :
			(lastCommitUpdateTime.isAfter(calculatedCursor) ? lastCommitUpdateTime : calculatedCursor);

		try {
			String accessToken = userQueryService.getUserGitHubAccessToken(gitHubId);

			List<String> repos = gitHubService.fetchRepos(accessToken, gitHubId);
			ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			List<CompletableFuture<Void>> futures = new ArrayList<>();

			Map<LocalDateTime, Integer> commitsByDate = new ConcurrentHashMap<>();

			for (String fullName : repos) {
				CompletableFuture<Void> voidCompletableFuture = CompletableFuture.runAsync(() -> {
					gitHubService.countCommits(accessToken, fullName, gitHubId, since, commitsByDate);
				}, executor);
				futures.add(voidCompletableFuture);
			}

			CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			allFutures.join();
			executor.shutdown();

			saveCommits(user, commitsByDate);

			expService.calculateExpAndTier(gitHubId);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return CommitResponse.of(true, user);
	}

	private void saveCommits(User user, Map<LocalDateTime, Integer> commitsByDate) {
		for (Map.Entry<LocalDateTime, Integer> entry : commitsByDate.entrySet()) {
			LocalDateTime day = entry.getKey().toLocalDate().atStartOfDay();
			int commitCounts = entry.getValue();

			Commit commit = Commit.create(day, commitCounts, user);
			commitRepository.save(commit);
		}
	}
}
