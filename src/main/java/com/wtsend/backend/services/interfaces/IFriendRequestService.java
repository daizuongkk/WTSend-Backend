package com.wtsend.backend.services.interfaces;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wtsend.backend.dto.request.AddFriendRequest;
import com.wtsend.backend.dto.response.FriendRequestResponse;

public interface IFriendRequestService {
  void sendFriendRequest(UUID from, AddFriendRequest request);

  void acceptFriendRequest(UUID from, Long id);

  void rejectFriendRequest(UUID from, Long id);

  Map<String, List<FriendRequestResponse>> getFriendRequests(String from);
}
