package com.wtsend.backend.services.interfaces;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

@Service
public interface IOAuth2Service {
	GoogleIdToken.Payload verify(String token);
}
