package com.wtsend.backend.dtos.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
	private String currentPassword;
	private String newPassword;
}
