package com.wtsend.backend.dto;

public enum MessageType {
  TEXT,
  AUDIO,
  IMAGE,
  VIDEO,
  FILE;

  public boolean accepts(String contentType) {
    return switch (this) {
      case IMAGE -> contentType.startsWith("image/");
      case VIDEO -> contentType.startsWith("video/");
      case AUDIO -> contentType.startsWith("audio/");
      case FILE -> true;
      default -> false;
    };
  }
}
