package com.wtsend.backend.services.interfaces;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;

@Service
public interface IUserService {
	public abstract UserResponse createUser(SignUpRequest request);

	public abstract UserResponse me(Jwt jwt);

	public abstract UserResponse findById(String id);

	public abstract UserResponse findByUsername(String username);

	public abstract String uploadAvatar(MultipartFile file, String userId);

	public abstract UserResponse updateUser(UpdateUserRequest request);

}
