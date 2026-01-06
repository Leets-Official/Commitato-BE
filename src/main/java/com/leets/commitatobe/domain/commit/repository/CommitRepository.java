package com.leets.commitatobe.domain.commit.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leets.commitatobe.domain.commit.domain.Commit;
import com.leets.commitatobe.domain.user.domain.User;

public interface CommitRepository extends JpaRepository<Commit, UUID> {
	List<Commit> findAllByUserAndCalculatedFalseOrderByCommitDateAsc(User user);

	List<Commit> findAllByUserAndCommitDate(User user, LocalDateTime commitDate);

	List<Commit> findAllByUserAndCommitDateIn(User user, List<LocalDateTime> dates);

	Optional<Commit> findTopByUserAndCalculatedIsTrueOrderByUpdatedAtDesc(User user);

	Optional<Commit> findByUserAndCommitDate(User user, LocalDateTime commitDate);

	@Query("SELECT c FROM commit c " +
		"WHERE c.user = :user " +
		"ORDER BY c.commitDate DESC")
	List<Commit> findCommitsByUser(@Param("user") User user);

	@Query("SELECT c.commitDate FROM commit c WHERE c.user = :user")
	List<LocalDateTime> findAllCommitDatesByUser(@Param("user") User user);
}
