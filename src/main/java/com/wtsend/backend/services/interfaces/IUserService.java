package com.wtsend.backend.services.interfaces;

import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.dto.response.UserSummaryResponse;

public interface IUserService {

  UserResponse findById(String id);

  UserSummaryResponse findByUsername(String username);

  String uploadAvatar(MultipartFile file, String userId);

  UserResponse updateUser(UpdateUserRequest request, String userId);

}
