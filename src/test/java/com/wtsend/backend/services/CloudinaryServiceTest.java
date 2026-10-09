package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;

/**
 * Any content type at all used to be forwarded to Cloudinary, so a PDF or a
 * video could be submitted as an avatar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudinaryServiceTest {

	@Mock
	private Cloudinary cloudinary;
	@Mock
	private Uploader uploader;

	private CloudinaryService service;

	@BeforeEach
	void setUp() {
		service = new CloudinaryService(cloudinary);
		when(cloudinary.uploader()).thenReturn(uploader);
	}

	private static MockMultipartFile file(String contentType) {
		return new MockMultipartFile("img", "avatar", contentType, new byte[] { 1, 2, 3 });
	}

	@ParameterizedTest
	@ValueSource(strings = { "image/jpeg", "image/png", "image/webp", "IMAGE/PNG" })
	void acceptsTheAllowedImageTypes(String contentType) throws Exception {
		when(uploader.upload(any(), any())).thenReturn(Map.of("secure_url", "u", "public_id", "p"));

		// Map<?, ?> defeats containsKey, so assert the call reached Cloudinary.
		assertThat(service.upload(file(contentType))).isNotNull();
		verify(uploader).upload(any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "application/pdf", "video/mp4", "text/html", "image/svg+xml" })
	void rejectsEverythingElse(String contentType) throws Exception {
		assertThatThrownBy(() -> service.upload(file(contentType)))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);

		verify(uploader, never()).upload(any(), any());
	}

	@Test
	void rejectsAnEmptyFile() {
		assertThatThrownBy(() -> service.upload(new MockMultipartFile("img", new byte[0])))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.FILE_REQUIRED);
	}

	@Test
	void destroyingTheReplacedAssetDoesNotPropagateFailures() throws Exception {
		// It runs after the replacement is already stored, so throwing here would
		// fail a profile update that actually succeeded.
		when(uploader.destroy(eq("old-id"), any())).thenThrow(new RuntimeException("cloudinary down"));

		assertThatCode(() -> service.destroyQuietly("old-id")).doesNotThrowAnyException();
	}

	@Test
	void destroyingSkipsAUserWhoNeverHadAnAvatar() throws Exception {
		service.destroyQuietly(null);
		service.destroyQuietly("  ");

		verify(uploader, never()).destroy(any(), any());
	}
}
