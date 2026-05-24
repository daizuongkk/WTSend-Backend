package com.wtsend.backend.configs.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.wtsend.backend.models.User;
import com.wtsend.backend.repositories.UserRepository;
import com.wtsend.backend.services.JwtService;
import com.wtsend.backend.services.RefreshTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final RefreshTokenService refreshTokenService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication) throws IOException {

		OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
		String email = oAuth2User.getAttribute("email");

		User user = userRepository.findByEmail(email)
				.orElseGet(() -> createUser(oAuth2User));

		String accessToken = jwtService.generateToken(user);
		String refreshToken = refreshTokenService.create(user).getToken();
		response.setContentType("application/json");
		response.getWriter().write(
				"{\"accessToken\":\"" + accessToken + "\",\"refreshToken\":\"" + refreshToken + "\"}");
	}

	private User createUser(OAuth2User oAuth2User) {
		String email = oAuth2User.getAttribute("email");

		return User.builder().username(
				email.split("@")[0]).email(email).avatarUrl(oAuth2User.getAttribute("picture"))
				.displayName(oAuth2User.getAttribute("name")).build();
	}
}