package com.wtsend.backend.services.interfaces;

import java.util.UUID;

import com.wtsend.backend.dto.request.InitUploadRequest;
import com.wtsend.backend.dto.response.InitUploadResponse;

public interface IMediaService {
  public InitUploadResponse initUpload(UUID userId, InitUploadRequest request);

}
