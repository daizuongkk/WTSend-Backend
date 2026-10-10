package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;

/**
 * What {@code PUT /api/users} does to an avatar.
 *
 * <p>
 * The bug this pins needed two instances of the same row to show itself, which
 * is exactly what a mocked repository makes visible: {@code updateUser} held a
 * detached user, {@code uploadAvatar} loaded and saved a second one, and the
 * first was then written over the top -- after the asset it pointed at had
 * already been destroyed.
 */
@ExtendWith(MockitoExtension.class)
class AvatarUpdateTest {

	private static final String USER_ID = "user-1";

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserUtils userMapper;
	@Mock
	private CloudinaryService cloudinary;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private UserService userService;

	private User stored;

	@BeforeEach
	void setUp() {
		stored = new User();
		stored.setId(USER_ID);
		stored.setDisplayName("Ai Đó");
		stored.setAvatarUrl("https://res.cloudinary.com/x/old.png");
		stored.setAvatarId("wtsend/avatars/old");
	}

	private MockMultipartFile anImage() {
		return new MockMultipartFile("avatar", "new.png", "image/png", new byte[] { 1, 2, 3 });
	}

	@Test
	void updatingWithAnAvatarKeepsTheNewAssetAndDestroysOnlyTheOldOne() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
		when(cloudinary.upload(any())).thenAnswer(inv -> Map.of(
				"secure_url", "https://res.cloudinary.com/x/new.png",
				"public_id", "wtsend/avatars/new"));

		UpdateUserRequest request = new UpdateUserRequest();
		request.setAvatar(anImage());
		request.setBio("xin chào");

		userService.updateUser(request, USER_ID);

		// The row that gets saved must carry the NEW asset. It used to be saved
		// with the old values, pointing at an asset that had just been deleted.
		assertThat(stored.getAvatarUrl()).isEqualTo("https://res.cloudinary.com/x/new.png");
		assertThat(stored.getAvatarId()).isEqualTo("wtsend/avatars/new");
		assertThat(stored.getBio()).isEqualTo("xin chào");

		// Saved once, and the destroy happens after it -- not before, or a failed
		// save would leave the row pointing at a deleted asset.
		InOrder order = inOrder(userRepository, cloudinary);
		order.verify(userRepository).save(stored);
		order.verify(cloudinary).destroyQuietly("wtsend/avatars/old");
	}

	@Test
	void aFirstEverUploadDoesNotRevertToNullAndDestroysNothing() {
		stored.setAvatarUrl(null);
		stored.setAvatarId(null);
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
		when(cloudinary.upload(any())).thenAnswer(inv -> Map.of(
				"secure_url", "https://res.cloudinary.com/x/first.png",
				"public_id", "wtsend/avatars/first"));

		UpdateUserRequest request = new UpdateUserRequest();
		request.setAvatar(anImage());

		userService.updateUser(request, USER_ID);

		// This was the worse half: nothing to destroy, but the row reverted to
		// NULL, so the avatar silently never saved.
		assertThat(stored.getAvatarUrl()).isEqualTo("https://res.cloudinary.com/x/first.png");
		verify(cloudinary).destroyQuietly(null);
	}

	@Test
	void anUpdateWithNoAvatarTouchesCloudinaryNotAtAll() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

		UpdateUserRequest request = new UpdateUserRequest();
		request.setBio("chỉ đổi bio");

		userService.updateUser(request, USER_ID);

		verify(cloudinary, never()).upload(any());
		verify(cloudinary).destroyQuietly(null);
		assertThat(stored.getAvatarId()).isEqualTo("wtsend/avatars/old");
	}

	@Test
	void anUploadResponseMissingAFieldIsReportedRatherThanDereferenced() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
		// A successful upload whose response has no secure_url: used to NPE into
		// a 500 with the asset already uploaded and billed, and nothing recorded,
		// so it could never be destroyed.
		when(cloudinary.upload(any())).thenAnswer(inv -> Map.of("public_id", "wtsend/avatars/new"));

		UpdateUserRequest request = new UpdateUserRequest();
		request.setAvatar(anImage());

		assertThatThrownBy(() -> userService.updateUser(request, USER_ID))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);

		verify(userRepository, never()).save(any());
	}

	@Test
	void theStandaloneUploadEndpointStillWorksOnItsOwn() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
		when(cloudinary.upload(any())).thenAnswer(inv -> Map.of(
				"secure_url", "https://res.cloudinary.com/x/new.png",
				"public_id", "wtsend/avatars/new"));

		// POST /api/users/upload is used by another client, so splitting the
		// Cloudinary call out of it must not change what it does.
		String url = userService.uploadAvatar(anImage(), USER_ID);

		assertThat(url).isEqualTo("https://res.cloudinary.com/x/new.png");
		InOrder order = inOrder(userRepository, cloudinary);
		order.verify(userRepository).save(stored);
		order.verify(cloudinary).destroyQuietly("wtsend/avatars/old");
	}
}
