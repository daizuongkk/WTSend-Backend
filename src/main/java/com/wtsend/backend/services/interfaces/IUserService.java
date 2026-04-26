package com.wtsend.backend.services.interfaces;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.response.UserResponse;

@Service
public interface IUserService {
	public abstract UserResponse createUser(SignUpRequest request);

	public abstract UserResponse me(Jwt jwt);

	public abstract UserResponse findById(String id);

}
