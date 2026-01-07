package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

		LocalDateTime since = LocalDate.now().minusMonths(2).withDayOfMonth(1).atStartOfDay();
		saveCommits(user, commitsByDate, since);
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
		if (user == null)
			return;

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
		LocalDateTime since = LocalDate.now().minusMonths(2).withDayOfMonth(1).atStartOfDay();

		Map<LocalDateTime, Integer> commitsByDate = new ConcurrentHashMap<>();

		gitHubService.fetchRepos(accessToken)
			.forEach(name ->
				gitHubService.countCommits(accessToken, name, user.getGithubId(), since, commitsByDate));

		saveCommits(user, commitsByDate, since);

		expService.calculateExpAndTier(user.getGithubId());
	}

	private void saveCommits(User user, Map<LocalDateTime, Integer> commitsByDate, LocalDateTime since) {
		List<Commit> existingCommits = commitRepository.findAllByUserAndCommitDateAfter(user, since.minusSeconds(1));

		List<Commit> toSave = new ArrayList<>();
		List<Commit> toDelete = new ArrayList<>();

		for (Commit commit : existingCommits) {
			LocalDateTime date = commit.getCommitDate();

			if (commitsByDate.containsKey(date)) {
				int newCnt = commitsByDate.get(date);
				commit.updateCnt(newCnt);
				commitsByDate.remove(date);
			} else {
				commit.updateCnt(0);
			}

			if (commit.getCnt() <= 0) {
				toDelete.add(commit);
			} else {
				toSave.add(commit);
			}
		}

		commitsByDate.forEach((date, cnt) -> {
			if (cnt > 0) {
				toSave.add(Commit.create(date, cnt, user));
			}
		});

		if (!toDelete.isEmpty()) {
			commitRepository.deleteAll(toDelete);
		}
		commitRepository.saveAll(toSave);
	}
}
