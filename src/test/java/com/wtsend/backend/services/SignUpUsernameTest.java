package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;

/**
 * A password registration ends up with a username.
 *
 * <p>
 * It used to end up with none: {@code UserUtils.toUser} set every other field
 * and left this one null, and nothing downstream noticed. The column is the
 * only key {@code GET /users/search} accepts, so those accounts could not be
 * found by anybody trying to add them as a friend -- the blank field in their
 * own profile was the visible half of a bug that mattered more than it looked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignUpUsernameTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserUtils userMapper;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private AuthService authService;

	/** What the real mapper produces for this address, padded to the minimum. */
	private static final String DERIVED = "john0000";

	private SignUpRequest signUp() {
		return SignUpRequest.builder().email("john@gmail.com").password("Passw0rd!").build();
	}

	private User mappedWith(String username) {
		User user = new User();
		user.setEmail("john@gmail.com");
		user.setDisplayName("john");
		user.setUsername(username);
		return user;
	}

	private String savedUsername() {
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		return saved.getValue().getUsername();
	}

	@Test
	void theDerivedUsernameIsStored() {
		when(userMapper.toUser(any())).thenReturn(mappedWith(DERIVED));
		when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		when(userMapper.toUserResponse(any())).thenReturn(new UserResponse());

		authService.signUp(signUp());

		assertThat(savedUsername()).isEqualTo(DERIVED);
	}

	@Test
	void aTakenUsernameGetsASuffixRatherThanAConstraintViolation() {
		when(userMapper.toUser(any())).thenReturn(mappedWith(DERIVED));
		when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		when(userMapper.toUserResponse(any())).thenReturn(new UserResponse());
		// The local part is not unique: john@gmail.com and john@company.com derive
		// the same thing, and the column is UNIQUE. Nothing asks the user for a
		// username, so nothing else would settle this.
		when(userRepository.existsByUsername(DERIVED)).thenReturn(true);

		authService.signUp(signUp());

		assertThat(savedUsername()).isNotEqualTo(DERIVED).startsWith("john");
	}

	@Test
	void theStoredUsernameAlwaysFitsWhatTheProfileEndpointAccepts() {
		when(userMapper.toUser(any())).thenReturn(mappedWith(DERIVED));
		when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		when(userMapper.toUserResponse(any())).thenReturn(new UserResponse());
		// Three takers, so the suffix has to grow -- and it still cannot push the
		// value outside the 8-20 that PUT /api/users enforces, or the user could
		// never save their own profile again.
		when(userRepository.existsByUsername(any())).thenReturn(true, true, true, false);

		authService.signUp(signUp());

		assertThat(savedUsername().length())
				.isBetween(UserUtils.MIN_USERNAME_LENGTH, UserUtils.MAX_USERNAME_LENGTH);
	}
}
