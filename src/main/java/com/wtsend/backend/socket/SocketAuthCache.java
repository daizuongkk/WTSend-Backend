package com.wtsend.backend.socket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.wtsend.backend.dto.response.UserResponse;

import lombok.Getter;

@Component
@Getter
public class SocketAuthCache {
	private final Map<String, UserResponse> cache = new ConcurrentHashMap<>();

	public void put(String token, UserResponse user) {
		cache.put(token, user);
	}

	public UserResponse remove(String token) {
		return cache.remove(token);
	}
}
