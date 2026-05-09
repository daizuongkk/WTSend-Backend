package com.wtsend.backend.services;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.request.UpdateUserRequest;
import com.wtsend.backend.dtos.response.UserResponse;
import com.wtsend.backend.exceptions.DuplicateResourceException;
import com.wtsend.backend.exceptions.RequestException;
import com.wtsend.backend.exceptions.ResourceNotFoundException;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.models.User;
import com.wtsend.backend.repositories.UserRepository;
import com.wtsend.backend.services.interfaces.IUserService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {
	private final UserRepository userRepository;

	private final UserUtils userMapper;

	private final CloudinaryService cloudinary;

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

	public String uploadAvatar(MultipartFile file, String userId) {
		Map<?, ?> result = cloudinary.upload(file);

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new EntityNotFoundException("User not found by id: " + userId));

		user.setAvatarUrl(result.get("secure_url").toString());
		user.setAvatarId(result.get("public_id").toString());
		userRepository.save(user);

		if (user.getAvatarUrl() == null)
			throw new ResourceNotFoundException("avatar url is null");

		return user.getAvatarUrl();

	}

	@Override
	public UserResponse updateUser(UpdateUserRequest request) {

		User existUser = userRepository.findByUsername(request.getUsername())
				.orElseThrow(() -> new EntityNotFoundException("User not found by usrename: " + request.getUsername()));

		existUser.setUsername(request.getUsername());
		existUser.setEmail(request.getEmail());
		existUser.setDisplayName(request.getDisplayName());
		if (request.getPhone() != null)
			existUser.setPhone(request.getPhone());
		if (request.getBio() != null)
			existUser.setBio(request.getBio());

		if (request.getBirthday() != null) {
			existUser.setBirthday(LocalDate.parse(request.getBirthday(), DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		}
		userRepository.save(existUser);
		return userMapper.toUserResponse(existUser);
	}

}
