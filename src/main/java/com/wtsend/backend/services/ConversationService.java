package com.wtsend.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wtsend.backend.dto.request.CreateConversationRequest;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.event.ConversationCreatedEvent;
import com.wtsend.backend.event.ConversationReadEvent;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageMapper;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.GroupInfo;
import com.wtsend.backend.model.Message;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.enums.ConversationType;
import com.wtsend.backend.repository.ConversationRepository;
import com.wtsend.backend.repository.FriendRepository;
import com.wtsend.backend.repository.ParticipantRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IConversationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService implements IConversationService {

  private final MessageMapper messageMapper;
  private final ConversationRepository conversationRepo;
  private final UserRepository userRepository;
  private final FriendRepository friendRepo;
  private final ConversationMapper conversationMapper;
  private final ParticipantRepository participantRepo;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(readOnly = true)
  public List<Long> getConversationIdsByUserId(UUID userId) {
    return conversationRepo.getConversationId(userId);
  }

  @Override
  // open-in-view is off, so the session must be held open here: the mapper
  // walks participants, lastMessage and group, all of which are now lazy.
  @Transactional(readOnly = true)
  public List<ConversationResponse> getConversations(UUID userId) {
    List<Conversation> conversations = conversationRepo.findConversations(userId);

    return conversations.stream()
        .map(conversationMapper::toResponse).toList();
  }

  @Override
  // Was unannotated, so the membership checks, the save and the broadcast were
  // three unrelated units of work. It also has to be transactional for the
  // AFTER_COMMIT listener to fire at all -- without a transaction Spring drops
  // the event silently, and new-group would simply stop being delivered.
  @Transactional
  public ConversationResponse createConversation(CreateConversationRequest request, UUID userId) {
    ConversationType type = request.getType();
    String name = request.getName();
    // The same id twice would build two Participant rows for one user and fail
    // on uk_participants_conversation_user.
    List<UUID> memberIds = request.getMemberIds().stream().distinct().toList();

    if (type == ConversationType.DIRECT && memberIds.size() > 1)
      throw new AppException(ErrorCode.CONVERSATION_INVALID_TYPE)
          .withDetail("DIRECT with " + memberIds.size() + " members");

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

    // One query for the whole member list rather than one per member. Note it
    // cannot stand in for the existence check below: an id that matches no row
    // produces no stranger, so it used to pass straight through here.
    List<User> strangers = friendRepo.findNonFriendsAmong(userId, memberIds);
    if (!strangers.isEmpty())
      throw new AppException(ErrorCode.CANNOT_CONVERSE_WITH_STRANGER)
          .withDetail("memberIds=" + strangers.stream().map(User::getId).toList());

    List<User> members = userRepository.findAllById(memberIds);
    if (members.size() != memberIds.size()) {
      List<UUID> found = members.stream().map(User::getId).toList();
      // findAllById drops what it cannot find, so a group was created missing
      // the member nobody was told about -- and a DIRECT id nobody owned threw
      // USER_NOT_FOUND only because of a second lookup further down.
      throw new AppException(ErrorCode.USER_NOT_FOUND)
          .withDetail("memberIds not found=" + memberIds.stream().filter(id -> !found.contains(id)).toList());
    }

    Conversation conversation = type.equals(ConversationType.DIRECT) ? createDirectConversation(user, members)
        : createGroupConversation(user, members, name);
    conversationRepo.save(conversation);

    ConversationResponse response = conversationMapper.toResponse(conversation);

    // Both types, not just GROUP. A DIRECT conversation created here and left
    // without a message reached the other person only on their next full
    // load: nothing was published, and the first-message path that normally
    // covers it had not run. Every participant, creator included -- memberIds
    // leaves the creator out, so their other tabs and devices never heard
    // about it either.
    List<UUID> recipients = conversation.getParticipants().stream()
        .map(participant -> participant.getUser().getId())
        .toList();
    eventPublisher.publishEvent(new ConversationCreatedEvent(response, recipients));

    return response;
  }

  @Override
  @Transactional
  public void markAsSeen(Long conversationId, UUID userId) {
    // Fetch the whole graph up front: the response built below walks
    // participants, lastMessage and group, each of which is lazy now.
    Conversation conversation = conversationRepo.findByIdWithGraph(conversationId)
        .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND).withDetail("id=" + conversationId));

    // Membership first, before either early return. It used to be checked
    // third, which made this endpoint an existence oracle: a non-member could
    // tell "conversation exists and has messages" (5006) from "exists and is
    // empty" (200) from "does not exist". CONVERSATION_ACCESS_DENIED is also
    // what getMessages answers for the same condition.
    if (participantRepo.findByConversationIdAndUserId(conversation.getId(), userId).isEmpty())
      throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED)
          .withDetail("conversationId=" + conversationId + " userId=" + userId);

    if (conversation.getLastMessage() == null)
      return;

    // Read off the managed entity before the write below detaches it.
    Message lastMessage = conversation.getLastMessage();
    UUID lastSenderId = lastMessage.getSender().getId();

    // One statement, not load-mutate-save: see ParticipantRepository.markSeen
    // for the lost update that cost.
    participantRepo.markSeen(conversation.getId(), userId, lastMessage, Instant.now());

    // The sender of the newest message has nothing to be told about their own
    // message, so no receipt is broadcast -- but the row above is still
    // written. The early return used to sit before it, leaving the sender's
    // own lastReadAt and lastSeenMessage stuck at whenever they last read
    // somebody else's message.
    if (lastSenderId.equals(userId))
      return;

    // Read back, and that is the whole point of this line. markSeen is
    // @Modifying(clearAutomatically = true), so `conversation` above is
    // detached and still carrying its pre-update field values -- and because
    // findByIdWithGraph had already fetched every association, mapping it
    // raised no LazyInitializationException. It simply broadcast the unread
    // count the reader had just cleared and a seen marker one message behind,
    // which the client then wrote back over its own optimistic clear: the
    // badge re-lit and "seen" never advanced. Same shape as
    // MessageService.publishNewMessage, which already had to learn this.
    Conversation afterRead = conversationRepo.findByIdWithGraph(conversationId)
        .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND).withDetail("id=" + conversationId));

    // Mapped from the fresh read; broadcast by the AFTER_COMMIT listener.
    // Emitting from inside this method told clients a message had been read
    // before the write was durable.
    eventPublisher.publishEvent(new ConversationReadEvent(
        conversationMapper.toResponse(afterRead),
        messageMapper.toLastMessage(afterRead.getLastMessage())));

  }

  private Conversation createNewDirectConversation(User sender, User recipient) {
    Conversation convo = new Conversation();
    convo.setType(ConversationType.DIRECT);

    Participant p1 = createParticipant(sender, convo);

    Participant p2 = createParticipant(recipient, convo);

    convo.setDirectKey(Conversation.directKeyOf(sender.getId(), recipient.getId()));
    convo.setParticipants(new ArrayList<>(List.of(p1, p2)));

    return convo;
  }

  private Participant createParticipant(User user, Conversation convo) {
    return Participant.builder()
        .user(user)
        .conversation(convo)
        .unreadCounts(0L)
        .joinedAt(Instant.now())
        .build();
  }

  /** Members arrive already loaded and already checked for existence. */
  private Conversation createDirectConversation(User user, List<User> members) {
    User participant = members.get(0);

    if (participant.getId().equals(user.getId())) {
      throw new AppException(ErrorCode.CANNOT_CONVERSE_WITH_SELF);
    }

    return conversationRepo
        .findByDirectKey(Conversation.directKeyOf(user.getId(), participant.getId()))
        .orElseGet(() -> createNewDirectConversation(user, participant));
  }

  private Conversation createGroupConversation(User user, List<User> users, String name) {

    if (name == null || name.isBlank()) {
      name = "Nhóm của " + user.getDisplayName().split(" ")[0];
    }

    List<Participant> participants = new ArrayList<>();
    Conversation conversation = new Conversation();
    conversation.setType(ConversationType.GROUP);
    for (User u : users) {
      Participant p = createParticipant(u, conversation);
      participants.add(p);
    }
    participants.add(createParticipant(user, conversation));
    conversation.setParticipants(participants);
    // No avatarUrl until a group picture is uploaded -- it used to be set to the
    // group name, so any client rendering it as an <img src> fetched garbage.
    // Clients fall back to a composite of the members' avatars.
    GroupInfo groupInfo = GroupInfo.builder().conversation(conversation).name(name).creator(user)
        .build();
    conversation.setGroup(groupInfo);

    return conversation;
  }

}
