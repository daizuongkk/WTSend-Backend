package com.wtsend.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dto.response.FriendResponse;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.Friend;
import com.wtsend.backend.repository.FriendRepository;
import com.wtsend.backend.services.interfaces.IFriendService;

@Service
public class FriendService implements IFriendService {

	private final FriendRepository friendRepo;

	private final UserUtils userUtils;

	FriendService(UserUtils userUtils, FriendRepository friendRepo) {
		this.friendRepo = friendRepo;
		this.userUtils = userUtils;
	}

	@Override
	public List<FriendResponse> getAllFriends(String userId) {
		List<Friend> friends = friendRepo.findFriends(userId);

		return friends.stream()
				.map(fr -> fr.getUserA().getId().equals(userId) ? userUtils.toFriendResponse(fr.getUserB())
						: userUtils
								.toFriendResponse(fr
										.getUserA()))
				.toList();
	}

	public boolean isFriend(String userA, String userB) {

		return friendRepo.existsByUserAIdAndUserBId(userA, userB) || friendRepo.existsByUserAIdAndUserBId(userB, userA);
	}

}
