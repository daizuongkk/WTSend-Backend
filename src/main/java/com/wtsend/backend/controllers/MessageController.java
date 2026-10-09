package com.wtsend.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.MessagesResponse;
import com.wtsend.backend.services.interfaces.IMessageService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/messages")
@Validated
public class MessageController {

	private final IMessageService messageService;

	MessageController(IMessageService messageService) {
		this.messageService = messageService;
	}

	@PostMapping("/direct")
	/**
	 * Returns the stored message rather than a string. The client shows a message
	 * optimistically and needs the real id to reconcile it; the socket echo alone
	 * meant guessing which echo matched which send.
	 */
	public ResponseEntity<MessageResponse> sendDirectMessage(@RequestBody @Valid SendDirectMessageRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(messageService.sendDirectMessage(request, jwt.getSubject()));
	}

	@PostMapping("/group")
	public ResponseEntity<MessageResponse> sendGroupMessage(@RequestBody @Valid SendGroupMessageRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(messageService.sendGroupMessage(request, jwt.getSubject()));
	}

	@GetMapping("/{conversationId}")
	public ResponseEntity<MessagesResponse> getMessages(@PathVariable Long conversationId,
			@RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(100) int limit,
			// Opaque cursor, parsed in the service. Declared as Instant before, which
			// made the first page fail: required=false skips conversion only when the
			// parameter is absent, and the client sends an empty cursor=.
			@RequestParam(name = "cursor", required = false) String cursor,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(messageService.getMessages(conversationId, jwt.getSubject(), limit, cursor));
	}
}
