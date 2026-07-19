package com.wtsend.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
	private String currentPassword;
	private String newPassword;
}
