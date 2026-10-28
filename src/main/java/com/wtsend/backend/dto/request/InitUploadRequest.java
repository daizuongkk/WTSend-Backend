package com.wtsend.backend.dto.request;

/**
 * InitUploadRequest
 */
public record InitUploadRequest(

    String fileName,
    String contentType,
    Long size

) {

}
