package com.wtsend.backend.dto.request;

import java.util.List;

import com.wtsend.backend.model.enums.ConversationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateConversationRequest(

    @NotNull(message = "type is required") ConversationType type,

    /**
     * Bounded by group_info.name, so an over-long one is a 400 and not a 500.
     */
    @Size(max = 255, message = "name must be at most 255 characters") String name,

    /**
     * {@code @NotBlank} on the element, not just {@code @NotEmpty} on the list.
     * {@code memberIds: [null]} passed validation and reached
     * {@code participantId.equals(...)} as an NPE, and {@code [""]} reached the
     * database as a lookup that silently found nobody.
     */
    @NotEmpty(message = "list member can not empty") List<@NotBlank(message = "member id can not be blank") String> memberIds

) {
}