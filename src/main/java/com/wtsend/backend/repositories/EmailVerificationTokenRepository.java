package com.wtsend.backend.repositories;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.models.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends CrudRepository<EmailVerificationToken, String> {

}
