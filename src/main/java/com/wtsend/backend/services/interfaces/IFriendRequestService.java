package com.wtsend.backend.services.interfaces;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.AddFriendRequest;
import com.wtsend.backend.dtos.response.FriendRequestResponse;

@Service
public interface IFriendRequestService {
	public abstract void sendFriendRequest(String from, AddFriendRequest request);

	public abstract void acceptFriendRequest(String from, Long id);

	public abstract void rejectFriendRequest(String from, Long id);

	public abstract Map<String, List<FriendRequestResponse>> getFriendRequests(String from);
}
