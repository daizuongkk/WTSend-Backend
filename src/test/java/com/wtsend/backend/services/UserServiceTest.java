package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;

/**
 * Guards the account-takeover fix: updateUser used to resolve its target from
 * the request body, so any authenticated user could overwrite anyone's record.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

	private static final String CALLER = "caller-id";
	private static final String VICTIM_USERNAME = "victim-username";

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserUtils userMapper;
	@Mock
	private CloudinaryService cloudinary;

	@InjectMocks
	private UserService userService;

	@Test
	void updateUserWritesToTheAuthenticatedSubjectNotTheRequestBody() {
		User caller = new User();
		caller.setId(CALLER);
		caller.setUsername("caller-username");
		caller.setEmail("caller@example.com");

		when(userRepository.findById(CALLER)).thenReturn(Optional.of(caller));
		when(userRepository.existsByUsername(VICTIM_USERNAME)).thenReturn(false);
		when(userMapper.toUserResponse(any(User.class))).thenReturn(new UserResponse());

		UpdateUserRequest request = new UpdateUserRequest();
		request.setUsername(VICTIM_USERNAME);
		request.setDisplayName("attacker");

		userService.updateUser(request, CALLER);

		// The victim's record is never even looked up.
		verify(userRepository, never()).findByUsername(VICTIM_USERNAME);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(saved.getValue().getId()).isEqualTo(CALLER);
	}

	@Test
	void changingEmailResetsVerification() {
		User caller = new User();
		caller.setId(CALLER);
		caller.setEmail("old@example.com");
		ReflectionTestUtils.setField(caller, "emailVerified", true);

		when(userRepository.findById(CALLER)).thenReturn(Optional.of(caller));
		when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
		when(userMapper.toUserResponse(any(User.class))).thenReturn(new UserResponse());

		UpdateUserRequest request = new UpdateUserRequest();
		request.setEmail("new@example.com");

		userService.updateUser(request, CALLER);

		assertThat(caller.isEmailVerified()).isFalse();
	}
}
