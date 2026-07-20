package com.wtsend.backend.services.interfaces;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

public interface IOAuth2Service {
	GoogleIdToken.Payload verify(String token);
}
