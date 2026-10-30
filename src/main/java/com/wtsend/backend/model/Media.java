package com.wtsend.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.wtsend.backend.dto.request.InitUploadRequest;
import com.wtsend.backend.model.enums.MediaStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Builder
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@AllArgsConstructor
public class Media {
  @Id
  private UUID id;
  private String ownerId;
  private String originalName;
  private String contentType;
  private Long sizeBytes;
  private String storageKey;
  private String thumbnailKey;
  @Enumerated(EnumType.STRING)
  private MediaStatus status;
  private Integer width;
  private Integer height;
  private Integer durationSec;
  private String failReason;
  private Instant createdAt;
  private Instant updatedAt;

  public static Media pending(UUID id, String ownerId, InitUploadRequest req, String key) {
    Media m = new Media();
    m.id = id;
    m.ownerId = ownerId;
    m.originalName = req.fileName();
    m.contentType = req.contentType();
    m.sizeBytes = req.size();
    m.storageKey = key;
    m.status = MediaStatus.PENDING;
    return m;
  }

}
