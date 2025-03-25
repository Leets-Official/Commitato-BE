package com.leets.commitatobe.domain.user.index;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.domain.UserDocument;
import com.leets.commitatobe.domain.user.repository.UserRepository;
import com.leets.commitatobe.domain.user.repository.UserSearchRepository;

@Component
public class UserIndexRunner implements CommandLineRunner {
	private final UserRepository userRepository;
	private final UserSearchRepository userSearchRepository;
	public UserIndexRunner(UserRepository userRepository, UserSearchRepository userSearchRepository){
		this.userRepository = userRepository;
		this.userSearchRepository = userSearchRepository;
	}

	@Override
	public void run(String... args) {
		List<User> users = userRepository.findAll();
		for (User user : users) {
			UserDocument document = new UserDocument();
			document.setId(user.getId().toString());
			document.setGithubId(user.getGithubId());
			document.setUsername(user.getUsername());
			// 다른 필요한 필드도 변환
			userSearchRepository.save(document);
		}
	}
}
