package com.wtsend.backend.services.interfaces;

import java.util.List;
import java.util.Map;

import com.wtsend.backend.dto.request.AddFriendRequest;
import com.wtsend.backend.dto.response.FriendRequestResponse;

public interface IFriendRequestService {
	void sendFriendRequest(String from, AddFriendRequest request);

	void acceptFriendRequest(String from, Long id);

	void rejectFriendRequest(String from, Long id);

	Map<String, List<FriendRequestResponse>> getFriendRequests(String from);
}
