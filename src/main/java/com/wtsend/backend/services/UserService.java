package com.wtsend.backend.services;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.dto.response.UserSummaryResponse;
import com.wtsend.backend.event.OnlineVisibilityChangedEvent;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

  /** Must match the dd/MM/yyyy pattern validated on {@link UpdateUserRequest}. */
  private static final DateTimeFormatter BIRTHDAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final UserRepository userRepository;

  private final UserUtils userMapper;

  private final CloudinaryService cloudinary;

  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(readOnly = true)
  public UserResponse findById(UUID id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + id));
    return userMapper.toUserResponse(user);
  }

  @Transactional(readOnly = true)
  public UserSummaryResponse findByUsername(String username) {
    if (username == null || username.trim().isBlank())
      throw new AppException(ErrorCode.USERNAME_REQUIRED);

    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("username=" + username));

    // Not the full profile: search crosses users who have no relationship, so
    // it must not hand out email, phone, birthday or the verification flags.
    return UserSummaryResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .displayName(user.getDisplayName())
        .avatarUrl(user.getAvatarUrl())
        .build();
  }

  public String uploadAvatar(MultipartFile file, UUID userId) {
    // Resolve the user first: uploading before this check strands an asset in
    // Cloudinary whenever the id turns out to be bad.
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

    // Captured before the overwrite: the old id was simply discarded, so every
    // avatar change left an asset behind in Cloudinary forever.
    String replacedAvatarId = user.getAvatarId();

    applyUploadedAvatar(user, file);
    userRepository.save(user);

    // Only once the replacement is stored, and never fatally -- see
    // destroyQuietly.
    cloudinary.destroyQuietly(replacedAvatarId);

    return user.getAvatarUrl();

  }

  /**
   * Uploads the file and writes the result onto the user that is handed in --
   * without saving, and without destroying anything.
   *
   * <p>
   * Both of those are the caller's job, and that split is the whole point.
   * {@code updateUser} used to call {@code uploadAvatar}, which loaded a SECOND
   * instance of the same row, saved the new avatar onto it, and destroyed the
   * replaced asset; control then returned and {@code updateUser} saved its own
   * detached copy -- still carrying the pre-upload values -- over the top. The
   * row ended up pointing at an asset that had just been deleted, so the avatar
   * was broken for good, and the newly uploaded one was orphaned. On a first
   * upload it was worse: nothing to destroy, but the row reverted to NULL, so
   * the avatar silently never saved at all.
   */
  private void applyUploadedAvatar(User user, MultipartFile file) {
    Map<?, ?> result = cloudinary.upload(file);
    user.setAvatarUrl(requiredUploadField(result, "secure_url"));
    user.setAvatarId(requiredUploadField(result, "public_id"));
  }

  /**
   * A successful upload whose response is missing a field used to NPE here,
   * which surfaced as a 500 with the asset already uploaded and billed and
   * nothing recorded -- so it could never be destroyed either.
   */
  private static String requiredUploadField(Map<?, ?> result, String key) {
    Object value = result.get(key);
    if (value == null)
      throw new AppException(ErrorCode.FILE_UPLOAD_FAILED)
          .withDetail("Cloudinary response has no " + key);
    return value.toString();
  }

  @Override
  public UserResponse updateUser(UpdateUserRequest request, UUID userId) {

    // Keyed on the authenticated subject, never on the request body -- resolving
    // the target from client-supplied fields lets anyone edit anyone.
    User existUser = userRepository.findById(userId)
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

    if (request.getUsername() != null && !request.getUsername().equals(existUser.getUsername())) {
      if (userRepository.existsByUsername(request.getUsername()))
        throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
      existUser.setUsername(request.getUsername());
    }

    // Email is intentionally not updatable. It used to reset emailVerified,
    // which gave "unverified" two meanings and would have put established
    // accounts in scope for the 24-hour deletion of abandoned registrations.

    if (request.getDisplayName() != null)
      existUser.setDisplayName(request.getDisplayName());
    if (request.getPhone() != null)
      existUser.setPhone(request.getPhone());
    if (request.getBio() != null)
      existUser.setBio(request.getBio());

    if (request.getBirthday() != null) {
      // Empty clears it, the same contract as phone.
      existUser.setBirthday(request.getBirthday().isBlank()
          ? null
          : LocalDate.parse(request.getBirthday(), BIRTHDAY_FORMAT));
    }

    // Only on an actual change: without the comparison, editing a bio would
    // rebroadcast the user's presence to everyone.
    boolean visibilityChanged = request.getShowOnlineStatus() != null
        && request.getShowOnlineStatus() != existUser.isShowOnlineStatus();
    if (visibilityChanged)
      existUser.setShowOnlineStatus(request.getShowOnlineStatus());

    // Written onto the instance this method is about to save, not onto a second
    // one loaded elsewhere -- see applyUploadedAvatar for what that cost.
    String replacedAvatarId = null;
    if (request.getAvatar() != null) {
      replacedAvatarId = existUser.getAvatarId();
      applyUploadedAvatar(existUser, request.getAvatar());
    }

    userRepository.save(existUser);

    // Destroyed only once the replacement is durable: if the save had failed
    // after the destroy, the row would point at an asset that no longer exists.
    // Null-safe, so a first-ever upload passes straight through.
    cloudinary.destroyQuietly(replacedAvatarId);

    // After the save, not before. This method is deliberately not
    // @Transactional -- the Cloudinary round trips above would otherwise hold a
    // pooled database connection for their whole duration. So there is no
    // commit for the listener to hang off, and it runs with fallbackExecution:
    // publishing after the save is what puts it on the right side of the write.
    if (visibilityChanged)
      eventPublisher.publishEvent(
          new OnlineVisibilityChangedEvent(existUser.getId(), existUser.isShowOnlineStatus()));

    return userMapper.toUserResponse(existUser);
  }

}
