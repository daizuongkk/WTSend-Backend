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

import com.wtsend.backend.common.exception.SecurityErrorResponder;
import com.wtsend.backend.services.CustomUserDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	private final JwtAuthenticationConverter jwtAuthenticationConverter;

	private final CustomUserDetailsService userDetailsService;

	private final SecurityErrorResponder securityErrorResponder;

	private static final String[] WHITE_LIST = {
			// /login/** and /oauth2/** used to be here for a .oauth2Login() redirect
			// flow that was never registered in the filter chain. Both providers go
			// through POST endpoints instead, so those paths matched nothing.
			"/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/error",
			"/api/auth/google", "/api/auth/facebook", "/api/verify-email",
			// Both are for someone who cannot sign in, so neither can require auth.
			"/api/auth/forgot-password", "/api/auth/reset-password"

	};

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) {
		http.csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(
				corsConfigurationSource()))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(WHITE_LIST).permitAll()
						// Readable by an account that has not verified yet: the client needs
						// to know whether it has.
						.requestMatchers("/api/users/me").authenticated()
						// Authenticated, not public, and NOT EMAIL_VERIFIED. Public let any
						// site force a logout: the cookie is SameSite=None and CSRF is off,
						// so a page on another origin could POST here with credentials and
						// delete the server-side refresh record -- it cannot read the
						// response, but the victim is signed out of every tab regardless.
						// EMAIL_VERIFIED would be wrong in the other direction: unverified
						// users do get an access token and must still be able to log out.
						.requestMatchers("/api/auth/logout").authenticated()
						.requestMatchers("/api/send-verify-email").hasAuthority("EMAIL_NOT_VERIFIED")
						.requestMatchers("/api/**").hasAuthority("EMAIL_VERIFIED")
						.anyRequest().authenticated())
				// STATELESS, not IF_REQUIRED. Auth here is a bearer token and nothing
				// reads an HttpSession -- oauth2-client is used only to exchange a
				// Google authorization code server-side, never for a browser login
				// flow. With IF_REQUIRED, the request cache allocates a session while
				// translating an unauthenticated request into a 401, so anonymous
				// traffic could accumulate sessions nobody ever comes back for. It is
				// also the premise csrf.disable() below rests on.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				// Security rejects requests inside the filter chain, before any
				// @RestControllerAdvice runs. Without both halves wired, a 401 fell back
				// to sendError and every 403 -- including a failed EMAIL_VERIFIED check --
				// returned the container error page instead of an ErrorResponse.
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(securityErrorResponder)
						.accessDeniedHandler(securityErrorResponder));
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

		// No exposed headers: tokens travel in the JSON body and the refresh cookie,
		// so the Authorization header was exposed to the browser for nothing.

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		source.registerCorsConfiguration("/**", configuration);

		return source;
	}
}
