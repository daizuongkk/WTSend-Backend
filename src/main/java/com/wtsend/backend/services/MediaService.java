package com.wtsend.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.request.InitUploadRequest;
import com.wtsend.backend.dto.response.InitUploadResponse;
import com.wtsend.backend.dto.response.MediaResponse;
import com.wtsend.backend.event.MediaUploadedEvent;
import com.wtsend.backend.model.Media;
import com.wtsend.backend.model.enums.MediaStatus;
import com.wtsend.backend.repository.MediaRepository;
import com.wtsend.backend.services.interfaces.IMediaService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
@RequiredArgsConstructor
public class MediaService implements IMediaService {
  private final S3Presigner presigner;
  private final S3Client s3;
  private final MediaRepository repo;
  private final ApplicationEventPublisher events;

  @Value("${storage.bucket}")
  private String bucket;

  private static final Duration PUT_TTL = Duration.ofMinutes(10);
  private static final Duration GET_TTL = Duration.ofMinutes(10);

  private static final Map<String, String> EXT = Map.of(
      "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp",
      "video/mp4", ".mp4", "application/pdf", ".pdf");

  private long maxSizeFor(String ct) {
    if (ct.startsWith("image/"))
      return 10L * 1024 * 1024;
    if (ct.startsWith("video/"))
      return 100L * 1024 * 1024;
    return 20L * 1024 * 1024;
  }

  public InitUploadResponse initUpload(UUID userId, InitUploadRequest req) {
    if (!EXT.containsKey(req.contentType()) || req.size() > maxSizeFor(req.contentType()))
      throw new AppException(ErrorCode.INVALID_REQUEST);

    UUID id = UUID.randomUUID();
    String key = "media/%s/%s%s".formatted(LocalDate.now(ZoneId.systemDefault()), id, EXT.get(req.contentType()));
    repo.save(Media.pending(id, userId, req, key));

    PutObjectRequest put = PutObjectRequest.builder()
        .bucket(bucket).key(key)
        .contentType(req.contentType())
        .contentLength(req.size())
        .build();

    String url = presigner.presignPutObject(r -> r
        .signatureDuration(PUT_TTL).putObjectRequest(put)).url().toString();

    return new InitUploadResponse(id, url, "PUT",
        Map.of("Content-Type", req.contentType()),
        Instant.now().plus(PUT_TTL));
  }

  @Transactional
  public MediaResponse completeUpload(UUID userId, UUID id) {
    Media m = repo.findByIdAndOwnerId(id, userId).orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
    if (m.getStatus() != MediaStatus.PENDING)
      return toResponse(m); // idempotent

    HeadObjectResponse head;
    try {
      head = s3.headObject(b -> b.bucket(bucket).key(m.getStorageKey()));
    } catch (NoSuchKeyException e) {
      throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
    } catch (S3Exception e) {
      throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
    }

    if (head.contentLength() != m.getSizeBytes().longValue())
      throw new AppException(ErrorCode.MEDIA_SIZE_MISMACTH);

    m.setStatus(MediaStatus.PROCESSING);
    m.setUpdatedAt(Instant.now());
    events.publishEvent(new MediaUploadedEvent(m.getId()));
    return toResponse(m);
  }

  public MediaResponse get(UUID userId, UUID id) {
    Media m = repo.findById(id).orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
    if (!m.getOwnerId().equals(userId))
      throw new AppException(ErrorCode.NOT_MEDIA_OWNER);
    return toResponse(m);
  }

  public MediaResponse toResponse(Media m) {
    String thumb = m.getThumbnailKey() == null ? null : presignGet(m.getThumbnailKey());
    return new MediaResponse(m.getId(), m.getOriginalName(), m.getContentType(),
        m.getSizeBytes(), m.getStatus(), m.getWidth(), m.getHeight(),
        m.getDurationSec(), thumb);
  }

  public String presignGet(String key) {
    GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
    return presigner.presignGetObject(r -> r
        .signatureDuration(GET_TTL).getObjectRequest(get)).url().toString();
  }
}
