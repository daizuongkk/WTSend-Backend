package com.wtsend.backend.controllers;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.request.InitUploadRequest;
import com.wtsend.backend.dto.response.InitUploadResponse;
import com.wtsend.backend.dto.response.MediaResponse;
import com.wtsend.backend.services.interfaces.IMediaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
  private final IMediaService mediaService;

  @PostMapping("/init")
  public InitUploadResponse init(@RequestBody @Valid InitUploadRequest req,
      @AuthenticationPrincipal Jwt jwt) {
    return mediaService.initUpload(jwt.getSubject(), req);
  }

  @PostMapping("/{id}/complete")
  public MediaResponse complete(@PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return mediaService.completeUpload(jwt.getSubject(), id);
  }

}
