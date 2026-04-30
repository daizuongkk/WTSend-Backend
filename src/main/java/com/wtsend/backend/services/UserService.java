package com.wtsend.backend.services;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.response.UserResponse;
import com.wtsend.backend.exceptions.DuplicateResourceException;
import com.wtsend.backend.exceptions.RequestException;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.models.User;
import com.wtsend.backend.repositories.UserRepository;
import com.wtsend.backend.services.interfaces.IUserService;

import jakarta.persistence.EntityNotFoundException;

@Service
public class UserService implements IUserService {
	private final UserRepository userRepository;

	private final UserUtils userMapper;

	UserService(UserRepository userRepository, UserUtils userMapper) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
	}

	public UserResponse createUser(SignUpRequest request) {

		if (userRepository.existsByUsername(request.getUsername())) {
			throw new DuplicateResourceException("Username already exists");
		}

		if (userRepository.existsByEmail(request.getEmail())) {
			throw new DuplicateResourceException("Email already exists");
		}

		User createdUser = userMapper.toUser(request);

		return userMapper.toUserResponse(userRepository.save(createdUser));
	}

	@Override
	public UserResponse me(Jwt jwt) {
		User user = userRepository.findById(jwt.getSubject())
				.orElseThrow(() -> new UsernameNotFoundException("User not found with UserId: " + jwt.getSubject()));

		return userMapper.toUserResponse(user);
	}

	@Override
	public UserResponse findById(String id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UsernameNotFoundException("User not found with UserId: " + id));
		return userMapper.toUserResponse(user);
	}

	public UserResponse findByUsername(String username) {
		if (username == null || username.trim().isBlank())
			throw new RequestException("Username is null");

		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));

		return userMapper.toUserResponse(user);
	}

}
