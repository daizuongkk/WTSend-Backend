package com.wtsend.backend.services.interfaces;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.response.FriendResponse;

@Service
public interface IFriendService {
	public abstract List<FriendResponse> getAllFriends(String userId);
}
