package com.leets.commitatobe.global.jwt.service;

import java.time.Instant;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leets.commitatobe.domain.auth.dto.GithubToken;
import com.leets.commitatobe.domain.auth.dto.LoginResponse;
import com.leets.commitatobe.domain.auth.service.GithubTokenService;
import com.leets.commitatobe.domain.tier.domain.Tier;
import com.leets.commitatobe.domain.tier.repository.TierRepository;
import com.leets.commitatobe.domain.user.domain.User;
import com.leets.commitatobe.domain.user.repository.UserRepository;
import com.leets.commitatobe.global.jwt.dto.JwtResponse;
import com.leets.commitatobe.global.jwt.provider.JwtProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
	@Value("${spring.security.oauth2.client.registration.github.client-id}")
	private String clientId;

	@Value("${spring.security.oauth2.client.registration.github.client-secret}")
	private String clientSecret;

	@Autowired
	private JwtProvider jwtProvider;

	private final UserRepository userRepository;

	private final TierRepository tierRepository;

	private final GithubTokenService githubTokenService;

	public LoginResponse generateJwt(GithubToken token) {
		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("github")
			.clientId(clientId)
			.clientSecret(clientSecret)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/api/auth/github/callback")
			.tokenUri("https://github.com/login/oauth/access_token")
			.authorizationUri("https://github.com/login/oauth/authorize")
			.userInfoUri("https://api.github.com/user")
			.userNameAttributeName("login")  // GitHub의 로그인 이름 속성을 지정
			.build();

		OAuth2UserRequest userRequest = new OAuth2UserRequest(
			clientRegistration,
			new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token.accessToken(), Instant.now(),
				Instant.now().plusSeconds(3600))
		);

		return loadUserAndJwt(userRequest, token);
	}

	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oAuth2User = super.loadUser(userRequest);

		return new DefaultOAuth2User(
			Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
			oAuth2User.getAttributes(),
			"login");

	}

	public LoginResponse loadUserAndJwt(OAuth2UserRequest userRequest, GithubToken token) throws
		OAuth2AuthenticationException {
		OAuth2User oAuth2User = loadUser(userRequest);
		String githubId = oAuth2User.getAttribute("login");

		JwtResponse jwt = jwtProvider.generateTokenDto(githubId);

		Pair<User, Boolean> userWithStatus = userRepository.findByGithubId(githubId)
			.map(user -> Pair.of(user, false))
			.orElseGet(() -> Pair.of(createNewUser(oAuth2User), true));

		User user = userWithStatus.getFirst();
		boolean isNewUser = userWithStatus.getSecond();

		userRepository.save(user);

		githubTokenService.saveTokens(githubId, token);

		return LoginResponse.of(isNewUser, jwt);
	}

	private User createNewUser(OAuth2User oAuth2User) {
		String githubId = oAuth2User.getAttribute("login");
		String username = oAuth2User.getAttribute("name");
		String profileImage = oAuth2User.getAttribute("avatar_url");
		Tier tier = tierRepository.findByRequiredExp(0)
			.orElseThrow(() -> new IllegalStateException("기본 티어를 찾을 수 없습니다."));

		User user = User.builder()
			.githubId(githubId)
			.username(username)
			.profileImage(profileImage)
			.tier(tier)
			.exp(0)
			.build();

		return userRepository.save(user);
	}
}
