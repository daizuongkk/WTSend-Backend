package com.wtsend.backend.services;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.AddFriendRequest;
import com.wtsend.backend.dtos.response.FriendRequestResponse;
import com.wtsend.backend.exceptions.RequestException;
import com.wtsend.backend.exceptions.ResourceNotFoundException;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.models.Friend;
import com.wtsend.backend.models.FriendRequest;
import com.wtsend.backend.models.User;
import com.wtsend.backend.repositories.FriendRepository;
import com.wtsend.backend.repositories.FriendRequestRepository;
import com.wtsend.backend.repositories.UserRepository;
import com.wtsend.backend.services.interfaces.IFriendRequestService;

@Service
public class FriendRequestService implements IFriendRequestService {
	private final FriendRequestRepository friendRequestRepo;

	private final FriendRepository friendRepo;

	private final UserRepository userRepo;

	private final UserUtils userUtils;

	FriendRequestService(FriendRepository friendRepo, UserUtils userUtils, UserRepository userRepo,
			FriendRequestRepository friendRequestRepo) {
		this.friendRequestRepo = friendRequestRepo;
		this.friendRepo = friendRepo;
		this.userUtils = userUtils;
		this.userRepo = userRepo;
	}

	@Override
	public void sendFriendRequest(String from, AddFriendRequest request) {
		String to = request.getTo();
		String message = request.getMessage();

		if (from.equals(to)) {
			throw new RequestException("Cannot send friend request to yourself");
		}

		if (!userRepo.existsById(to)) {
			throw new RequestException("User not found by id: " + to);
		}

		if (friendRequestRepo.existsByFromUserIdAndToUserId(from, to)
				|| friendRequestRepo.existsByFromUserIdAndToUserId(to, from)) {
			throw new RequestException("Friend request already sent");
		}

		String low = (from.compareTo(to) > 0) ? to : from;
		String high = (from.compareTo(to) > 0) ? from : to;

		if (friendRepo.existsByUserAIdAndUserBId(low, high)) {
			throw new RequestException("Already friends");
		}

		User fromUser = userRepo.findById(from)
				.orElseThrow(() -> new RequestException("User not found"));

		User toUser = userRepo.findById(to)
				.orElseThrow(() -> new RequestException("User not found"));

		FriendRequest friendRequest = FriendRequest.builder().fromUser(fromUser)
				.toUser(toUser).message(message)
				.build();
		friendRequestRepo.save(friendRequest);

	}

	@Override
	public void acceptFriendRequest(String from, Long requestId) {

		FriendRequest request = friendRequestRepo.findById(requestId)
				.orElseThrow(() -> new ResourceNotFoundException("Friend request not found by id: " + requestId));

		if (!request.getToUser().getId().equals(from)) {
			throw new RequestException("Users do not have the right to refuse this request");
		}

		String fromId = request.getFromUser().getId();
		String toId = request.getToUser().getId();

		String low = fromId.compareTo(toId) > 0 ? toId : fromId;
		String high = fromId.compareTo(toId) > 0 ? fromId : toId;

		User userA = userRepo.findById(low)
				.orElseThrow(() -> new ResourceNotFoundException("User not found by id: " + low));

		User userB = userRepo.findById(high)
				.orElseThrow(() -> new ResourceNotFoundException("User not found by id: " + high));

		Friend friend = Friend.builder().userA(userA).userB(userB).build();

		friendRepo.save(friend);

		friendRequestRepo.delete(request);
	}

	@Override
	public void rejectFriendRequest(String from, Long requestId) {
		FriendRequest request = friendRequestRepo.findById(requestId)
				.orElseThrow(() -> new ResourceNotFoundException("Friend request not found by id: " + requestId));

		if (!request.getToUser().getId().equals(from)) {
			throw new RequestException("Users do not have the right to refuse this request");
		}

		friendRequestRepo.delete(request);

	}

	@Override
	public Map<String, List<FriendRequestResponse>> getFriendRequests(String userId) {

		List<FriendRequestResponse> sent = friendRequestRepo.findByFromUserId(userId).stream()
				.map(res -> FriendRequestResponse.builder().id(res.getId()).from(userUtils.toUserResponse(
						res.getFromUser())).to(userUtils.toUserResponse(
								res.getToUser()))
						.message(res.getMessage()).createdAt(res.getCreatedAt()).updatedAt(res.getUpdatedAt()).build())
				.toList();
		List<FriendRequestResponse> recieved = friendRequestRepo.findByToUserId(userId).stream()
				.map(res -> FriendRequestResponse.builder().id(res.getId()).from(userUtils.toUserResponse(
						res.getFromUser())).to(userUtils.toUserResponse(
								res.getToUser()))
						.message(res.getMessage()).createdAt(res.getCreatedAt()).updatedAt(res.getUpdatedAt()).build())
				.toList();
		return Map.of("sent", sent, "revieved", recieved);
	}

}
