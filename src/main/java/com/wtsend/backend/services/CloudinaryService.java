package com.wtsend.backend.services;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CloudinaryService {
	private final Cloudinary cloudinary;

	public Map<?, ?> upload(MultipartFile file) {
		if (file == null)
			throw new AppException(ErrorCode.FILE_REQUIRED);

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

}
