package com.wtsend.backend.configs.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.wtsend.backend.services.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	private final JwtAuthenticationConverter jwtAuthenticationConverter;

	private final CustomUserDetailsService userDetailsService;

	private static final String[] WHITE_LIST = {
			"/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/login/**", "/oauth2/**", "/error",
			"/api/auth/google", "/api/verify-email",
			// Logout only needs the refresh cookie. Gating it on EMAIL_VERIFIED left
			// unverified users -- who do get an access token -- unable to log out.
			"/api/auth/logout"

	};

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) {
		http.csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(
				corsConfigurationSource()))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(WHITE_LIST).permitAll()
						.requestMatchers(
								"/api/resend-otp",
								"/api/users/me")
						.authenticated()
						.requestMatchers("/api/send-verify-email").hasAuthority("EMAIL_NOT_VERIFIED")
						.requestMatchers("/api/**").hasAuthority("EMAIL_VERIFIED")
						.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint((request, response, authException) -> response
								.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage())));
		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager() {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder());

		return new ProviderManager(authenticationProvider);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Allowed origins come from config so that development patterns stay in the
	 * local profile. {@code https://*.ngrok-free.app} with allowCredentials(true)
	 * hands a credentialed cross-origin channel to anyone who can claim a free
	 * ngrok subdomain -- it must never be active in a deployed environment.
	 */
	/**
	 * Defaults to empty: an unconfigured profile allows no cross-origin traffic.
	 */
	@Value("${app.cors.allowed-origin-patterns:}")
	private List<String> allowedOriginPatterns;

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(allowedOriginPatterns);
		configuration.setAllowedMethods(List.of(
				"GET",
				"POST",
				"PUT",
				"PATCH",
				"DELETE",
				"OPTIONS"));

		configuration.setAllowedHeaders(List.of("*"));

		configuration.setAllowCredentials(true);

		configuration.setExposedHeaders(List.of(
				"Authorization"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		source.registerCorsConfiguration("/**", configuration);

		return source;
	}
}
