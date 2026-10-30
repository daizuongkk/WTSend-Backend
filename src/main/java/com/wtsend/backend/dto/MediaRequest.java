package com.wtsend.backend.dto;

/**
 * MediaRequest
 */
public record MediaRequest(
    String fileName,
    String fileType,
    String thumbnailUrl) {

}
