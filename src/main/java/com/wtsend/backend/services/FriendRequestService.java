package com.wtsend.backend.services;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wtsend.backend.dto.request.AddFriendRequest;
import com.wtsend.backend.dto.response.FriendRequestResponse;
import com.wtsend.backend.event.FriendshipCreatedEvent;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.Friend;
import com.wtsend.backend.model.FriendRequest;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.FriendRepository;
import com.wtsend.backend.repository.FriendRequestRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IFriendRequestService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FriendRequestService implements IFriendRequestService {
	private final FriendRequestRepository friendRequestRepo;

	private final FriendRepository friendRepo;

	private final UserRepository userRepo;

	private final UserUtils userUtils;

	private final ApplicationEventPublisher eventPublisher;

	@Override
	// One unit of work rather than five independent ones. It does not close the
	// race between the checks and the insert -- uk_friend_request_pair is the
	// arbiter for that -- but it does stop the checks reading a half-applied
	// accept, where the FriendRequest was gone and the Friend not yet visible.
	@Transactional
	public void sendFriendRequest(String from, AddFriendRequest request) {
		String to = request.getTo();
		String message = request.getMessage();

		if (from.equals(to)) {
			throw new AppException(ErrorCode.CANNOT_FRIEND_SELF);
		}

		if (!userRepo.existsById(to)) {
			throw new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + to);
		}

		if (friendRequestRepo.existsByFromUserIdAndToUserId(from, to)
				|| friendRequestRepo.existsByFromUserIdAndToUserId(to, from)) {
			throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
		}

		String low = (from.compareTo(to) > 0) ? to : from;
		String high = (from.compareTo(to) > 0) ? from : to;

		if (friendRepo.existsByUserAIdAndUserBId(low, high)) {
			throw new AppException(ErrorCode.ALREADY_FRIENDS);
		}

		User fromUser = userRepo.findById(from)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + from));

		User toUser = userRepo.findById(to)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + to));

		FriendRequest friendRequest = FriendRequest.builder().fromUser(fromUser)
				.toUser(toUser).message(message)
				.build();
		friendRequestRepo.save(friendRequest);

	}

	@Override
	@Transactional
	public void acceptFriendRequest(String from, Long requestId) {

		FriendRequest request = friendRequestRepo.findById(requestId)
				.orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND).withDetail("id=" + requestId));

		if (!request.getToUser().getId().equals(from)) {
			throw new AppException(ErrorCode.FRIEND_REQUEST_FORBIDDEN).withDetail("requestId=" + requestId + " userId=" + from);
		}

		// Already loaded on the request -- refetching them by id was two extra queries.
		User fromUser = request.getFromUser();
		User toUser = request.getToUser();

		// Accepting twice -- a double click, or a request left over from before the
		// pair became friends some other way -- hit uk_friend_pair and came back as
		// a 500. They are already friends, which is what the caller wanted, so this
		// clears the stale request and returns: accepting is idempotent. Throwing
		// here instead would roll the delete back and leave the row on screen for
		// the user to click again.
		if (friendRepo.existsFriendship(fromUser.getId(), toUser.getId())) {
			friendRequestRepo.delete(request);
			return;
		}

		// Friendships are stored with a canonical (low, high) ordering so the pair
		// is unique regardless of who sent the request.
		boolean fromIsLow = fromUser.getId().compareTo(toUser.getId()) <= 0;
		User userA = fromIsLow ? fromUser : toUser;
		User userB = fromIsLow ? toUser : fromUser;

		Friend friend = Friend.builder().userA(userA).userB(userB).build();

		friendRepo.save(friend);

		friendRequestRepo.delete(request);

		// Published, not emitted: this method is transactional, and presence for a
		// relationship that might still roll back is worse than presence a
		// moment late. The listener is what stops the two of them showing each
		// other offline until one reconnects.
		eventPublisher.publishEvent(new FriendshipCreatedEvent(userA.getId(), userB.getId()));
	}

	@Override
	public void rejectFriendRequest(String from, Long requestId) {
		FriendRequest request = friendRequestRepo.findById(requestId)
				.orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND).withDetail("id=" + requestId));

		if (!request.getToUser().getId().equals(from)) {
			throw new AppException(ErrorCode.FRIEND_REQUEST_FORBIDDEN).withDetail("requestId=" + requestId + " userId=" + from);
		}

		friendRequestRepo.delete(request);

	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, List<FriendRequestResponse>> getFriendRequests(String userId) {

		List<FriendRequestResponse> sent = friendRequestRepo.findByFromUserId(userId).stream()
				.map(res -> FriendRequestResponse.builder().id(res.getId()).from(userUtils.toUserResponse(
						res.getFromUser())).to(userUtils.toUserResponse(
								res.getToUser()))
						.message(res.getMessage()).createdAt(res.getCreatedAt()).updatedAt(res.getUpdatedAt()).build())
				.toList();
		List<FriendRequestResponse> received = friendRequestRepo.findByToUserId(userId).stream()
				.map(res -> FriendRequestResponse.builder().id(res.getId()).from(userUtils.toUserResponse(
						res.getFromUser())).to(userUtils.toUserResponse(
								res.getToUser()))
						.message(res.getMessage()).createdAt(res.getCreatedAt()).updatedAt(res.getUpdatedAt()).build())
				.toList();
		return Map.of("sent", sent, "received", received);
	}

}
