package com.wtsend.backend.dto.request;

import java.util.List;
import java.util.UUID;

import com.wtsend.backend.model.enums.ConversationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
/**
 * {@code @AllArgsConstructor} is what makes this deserialisable, and it is not
 * decoration: Lombok's {@code @Builder} generates an all-args constructor at
 * package level and suppresses the default one, so under Jackson 3 this class
 * had no creator at all and every request body carrying it answered 500.
 * Jackson matches the public constructor's parameters by name, which works
 * because Spring Boot compiles with {@code -parameters}.
 */
@AllArgsConstructor
@Builder
public class CreateConversationRequest {
  @NotNull(message = "type is required")
  private ConversationType type;

  /** Bounded by group_info.name, so an over-long one is a 400 and not a 500. */
  @Size(max = 255, message = "name must be at most 255 characters")
  private String name;

  /**
   * {@code @NotBlank} on the element, not just {@code @NotEmpty} on the list.
   * {@code memberIds: [null]} passed validation and reached
   * {@code participantId.equals(...)} as an NPE, and {@code [""]} reached the
   * database as a lookup that silently found nobody.
   */
  @NotEmpty(message = "Members is required")
  private List<@NotBlank(message = "member id can not be blank") UUID> memberIds;

}
