package com.wtsend.backend.dto.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupInfoResponse {
  private String name;

  private UUID creatorId;

  private String avatarUrl;
}
