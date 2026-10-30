package com.wtsend.backend.services.interfaces;

import java.util.UUID;

import com.wtsend.backend.dto.request.InitUploadRequest;
import com.wtsend.backend.dto.response.InitUploadResponse;
import com.wtsend.backend.dto.response.MediaResponse;

public interface IMediaService {
  public InitUploadResponse initUpload(UUID userId, InitUploadRequest request);

  MediaResponse completeUpload(UUID userId, UUID id);

}
