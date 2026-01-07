package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

@Service
@RequiredArgsConstructor
public class CommitUpdateService {
	private final UserRepository userRepository;
	private final CommitRepository commitRepository;
	private final ExpService expService;
	private final GithubTokenService githubTokenService;
	private final GitHubService gitHubService;


	@Transactional
	public void updateAndCalculate(UUID userId, Map<LocalDateTime, Integer> commitsByDate) {
		User user = userRepository.getReferenceById(userId);
		saveCommits(user, commitsByDate);
		expService.calculateExpAndTier(user.getGithubId());
	}

	@Transactional
	public void processHumanAccount(UUID userId) {
		userRepository.findById(userId)
			.ifPresent(User::changeHumanAccount);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void schedulerUpdateUserCommit(UUID userId) {
		User user = userRepository.findById(userId).orElse(null);
		if (user == null) return;

		String accessToken = githubTokenService.getDecryptedAccessToken(user.getGithubId())
			.orElseThrow(() -> new ApiException(ErrorStatus._UNAUTHORIZED));

		try {
			schedulerCommitUpdate(user, accessToken);
		} catch (ApiException e) {
			if (e.getErrorReasonHttpStatus().getHttpStatus() == HttpStatus.UNAUTHORIZED) {
				GithubToken newToken = githubTokenService.updateAccessTokenByRefreshToken(user.getGithubId());
				schedulerCommitUpdate(user, newToken.accessToken());
			} else {
				throw e;
			}
		}
	}

	private void schedulerCommitUpdate(User user, String accessToken) {
		LocalDateTime time = user.getLastCommitUpdateTime();
		if (time == null) {
			time = user.getCreatedAt().toLocalDate().atStartOfDay();
		}
		LocalDateTime since = time;

		Map<LocalDateTime, Integer> commitsByDate = new ConcurrentHashMap<>();

		// GitHub API 호출 (여전히 네트워크 I/O지만, 상위에서 유저별로 병렬 실행 중)
		gitHubService.fetchRepos(accessToken)
			.forEach(name ->
				gitHubService.countCommits(accessToken, name, user.getGithubId(), since, commitsByDate));

		saveCommits(user, commitsByDate);

		// EXP 계산 (이미 최적화됨)
		expService.calculateExpAndTier(user.getGithubId());
	}

	private void saveCommits(User user, Map<LocalDateTime, Integer> commitsByDate) {
		if (commitsByDate.isEmpty()) return;

		List<LocalDateTime> dates = new ArrayList<>(commitsByDate.keySet());
		List<Commit> existingCommits = commitRepository.findAllByUserAndCommitDateIn(user, dates);

		Map<LocalDateTime, Commit> commitMap = existingCommits.stream()
			.collect(Collectors.toMap(Commit::getCommitDate, Function.identity()));

		List<Commit> toSave = new ArrayList<>();

		commitsByDate.forEach((date, newCnt) -> {
			if (newCnt <= 0) return;
			LocalDateTime day = date.toLocalDate().atStartOfDay();

			Commit commit = commitMap.getOrDefault(day, Commit.create(day, 0, user));
			commit.addCnt(newCnt);
			toSave.add(commit);
		});

		commitRepository.saveAll(toSave);
	}
}
