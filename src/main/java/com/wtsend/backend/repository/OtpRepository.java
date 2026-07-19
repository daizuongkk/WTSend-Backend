package com.wtsend.backend.repository;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.models.Otp;

public interface OtpRepository extends CrudRepository<Otp, String> {
	Otp findByUserId(String userId);
}
