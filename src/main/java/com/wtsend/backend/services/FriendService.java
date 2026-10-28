package com.wtsend.backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  @Transactional(readOnly = true)
  public List<FriendResponse> getAllFriends(String userId) {
    List<Friend> friends = friendRepo.findFriends(userId);

    return friends.stream()
        .map(fr -> fr.getUserA().getId().equals(userId) ? userUtils.toFriendResponse(fr.getUserB())
            : userUtils
                .toFriendResponse(fr
                    .getUserA()))
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean isFriend(UUID userA, UUID userB) {

    return friendRepo.existsByUserAIdAndUserBId(userA, userB) || friendRepo.existsByUserAIdAndUserBId(userB, userA);
  }

}
