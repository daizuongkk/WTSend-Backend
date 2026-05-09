package com.wtsend.backend.libs.utils;

import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.response.FriendResponse;
import com.wtsend.backend.dtos.response.Sender;
import com.wtsend.backend.dtos.response.UserResponse;
import com.wtsend.backend.models.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserUtils {
	private final PasswordEncoder passwordEncoder;
	private final ModelMapper modelMapper;

	public User toUser(SignUpRequest request) {

		return User.builder().username(request.getUsername()).password(passwordEncoder.encode(request.getPassword()))
				.email(request.getEmail()).displayName(request.getFirstName() + " " + request.getLastName()).build();
	}

	public UserResponse toUserResponse(User user) {
		UserResponse userResponse = modelMapper.map(user, UserResponse.class);
		if (user.getBirthday() != null) {
			userResponse.setBirthday(user.getBirthday().toString());

		}
		return userResponse;
	}

	public FriendResponse toFriendResponse(User user) {
		return modelMapper.map(user, FriendResponse.class);
	}

	public Sender toSender(User user) {
		return Sender.builder()
				.id(user.getId())
				.displayName(user.getDisplayName())
				.avatarUrl(user.getAvatarUrl())
				.build();
	}
}
