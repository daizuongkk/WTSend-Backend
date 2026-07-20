package com.wtsend.backend.services.interfaces;

import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;

public interface IUserService {
	UserResponse createUser(SignUpRequest request);

	UserResponse me(String userId);

	UserResponse findById(String id);

	UserResponse findByUsername(String username);

	String uploadAvatar(MultipartFile file, String userId);

	UserResponse updateUser(UpdateUserRequest request, String userId);

}
