package com.wtsend.backend.services.interfaces;

import java.util.List;

import com.wtsend.backend.dto.response.FriendResponse;

public interface IFriendService {
	List<FriendResponse> getAllFriends(String userId);
}
