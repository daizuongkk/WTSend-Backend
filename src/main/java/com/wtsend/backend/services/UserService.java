package com.wtsend.backend.services;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.dto.response.UserSummaryResponse;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

	/** Must match the dd/MM/yyyy pattern validated on {@link UpdateUserRequest}. */
	private static final DateTimeFormatter BIRTHDAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final UserRepository userRepository;

	private final UserUtils userMapper;

	private final CloudinaryService cloudinary;

	public UserResponse createUser(SignUpRequest request) {

		if (userRepository.existsByEmail(request.getEmail())) {
			throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		User createdUser = userMapper.toUser(request);

		return userMapper.toUserResponse(userRepository.save(createdUser));
	}

	@Override
	public UserResponse me(String userId) {
		return findById(userId);
	}

	@Override
	public UserResponse findById(String id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + id));
		return userMapper.toUserResponse(user);
	}

	public UserSummaryResponse findByUsername(String username) {
		if (username == null || username.trim().isBlank())
			throw new AppException(ErrorCode.USERNAME_REQUIRED);

		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("username=" + username));

		// Not the full profile: search crosses users who have no relationship, so
		// it must not hand out email, phone, birthday or the verification flags.
		return UserSummaryResponse.builder()
				.id(user.getId())
				.username(user.getUsername())
				.displayName(user.getDisplayName())
				.avatarUrl(user.getAvatarUrl())
				.build();
	}

	public String uploadAvatar(MultipartFile file, String userId) {
		// Resolve the user first: uploading before this check strands an asset in
		// Cloudinary whenever the id turns out to be bad.
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

		Map<?, ?> result = cloudinary.upload(file);

		user.setAvatarUrl(result.get("secure_url").toString());
		user.setAvatarId(result.get("public_id").toString());
		userRepository.save(user);

		return user.getAvatarUrl();

	}

	@Override
	public UserResponse updateUser(UpdateUserRequest request, String userId) {

		// Keyed on the authenticated subject, never on the request body -- resolving
		// the target from client-supplied fields lets anyone edit anyone.
		User existUser = userRepository.findById(userId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

		if (request.getUsername() != null && !request.getUsername().equals(existUser.getUsername())) {
			if (userRepository.existsByUsername(request.getUsername()))
				throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
			existUser.setUsername(request.getUsername());
		}

		// Email is intentionally not updatable. It used to reset emailVerified,
		// which gave "unverified" two meanings and would have put established
		// accounts in scope for the 24-hour deletion of abandoned registrations.

		if (request.getDisplayName() != null)
			existUser.setDisplayName(request.getDisplayName());
		if (request.getPhone() != null)
			existUser.setPhone(request.getPhone());
		if (request.getBio() != null)
			existUser.setBio(request.getBio());

		if (request.getBirthday() != null) {
			// Empty clears it, the same contract as phone.
			existUser.setBirthday(request.getBirthday().isBlank()
					? null
					: LocalDate.parse(request.getBirthday(), BIRTHDAY_FORMAT));
		}

		if (request.getAvatar() != null) {
			uploadAvatar(request.getAvatar(), existUser.getId());
		}
		userRepository.save(existUser);
		return userMapper.toUserResponse(existUser);
	}

}
