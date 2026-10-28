package com.wtsend.backend.services.interfaces;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.dto.response.UserSummaryResponse;

public interface IUserService {

  UserResponse findById(UUID id);

  UserSummaryResponse findByUsername(String username);

  String uploadAvatar(MultipartFile file, UUID userId);

  UserResponse updateUser(UpdateUserRequest request, UUID userId);

}
