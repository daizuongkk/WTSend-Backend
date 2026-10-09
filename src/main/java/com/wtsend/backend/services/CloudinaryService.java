package com.wtsend.backend.services;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

	/**
	 * Anything outside this was accepted and forwarded, so a PDF or a video
	 * could become somebody's avatar.
	 *
	 * <p>
	 * The content type comes from the client's multipart header and is therefore
	 * spoofable -- this rejects the honest mistakes cheaply. Cloudinary itself
	 * inspects the bytes and rejects a mislabelled file, which surfaces as
	 * FILE_UPLOAD_FAILED. Size is not checked here: the
	 * {@code spring.servlet.multipart.*} limits reject an oversized part before
	 * the request reaches a controller, and GlobalExceptionHandler maps that to
	 * FILE_TOO_LARGE.
	 */
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp");

	private final Cloudinary cloudinary;

	public Map<?, ?> upload(MultipartFile file) {
		if (file == null || file.isEmpty())
			throw new AppException(ErrorCode.FILE_REQUIRED);

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase()))
			throw new AppException(ErrorCode.FILE_TYPE_NOT_ALLOWED).withDetail("contentType=" + contentType);

		try {
			return cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("folder", "wtsend/avatars",
					"transformation",
					new Transformation<>()
							.width(200)
							.height(200)
							.crop("fill")
							.gravity("face")
							.quality("auto")
							.fetchFormat("auto")));
		} catch (IOException e) {
			throw new AppException(ErrorCode.FILE_UPLOAD_FAILED, e);
		}
	}

	/**
	 * Removes an asset that is no longer referenced.
	 *
	 * <p>
	 * Never throws. It is only ever called once the replacement is already
	 * stored, so failing the caller would reject a profile update that had in
	 * fact succeeded. The cost of a failure is one orphaned asset, which is what
	 * the situation was before this method existed.
	 */
	public void destroyQuietly(String publicId) {
		if (publicId == null || publicId.isBlank())
			return;

		try {
			cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
		} catch (Exception e) {
			log.warn("Could not delete the replaced Cloudinary asset {}", publicId, e);
		}
	}
}
