package com.leets.commitatobe.domain.commit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leets.commitatobe.domain.commit.domain.Commit;
import com.leets.commitatobe.domain.commit.repository.CommitRepository;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommitUpdateService {
	private final UserRepository userRepository;
	private final CommitRepository commitRepository;
	private final ExpService expService;

	@Transactional
	public void updateAndCalculate(UUID userId, Map<LocalDateTime, Integer> commitsByDate) {
		User user = userRepository.getReferenceById(userId);
		saveCommits(user, commitsByDate);
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
