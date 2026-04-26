package com.wtsend.backend.controllers;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dtos.request.SendDirectMessageRequset;
import com.wtsend.backend.dtos.request.SendGroupMessageRequest;
import com.wtsend.backend.dtos.response.MessagesResponse;
import com.wtsend.backend.services.interfaces.IMessageService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

	private final IMessageService messageService;

	MessageController(IMessageService messageService) {
		this.messageService = messageService;
	}

	@PostMapping("/direct")
	public ResponseEntity<String> sendDirectMessage(@RequestBody @Valid SendDirectMessageRequset request,
			@AuthenticationPrincipal Jwt jwt) {
		messageService.sendDirectMessage(request, jwt.getSubject());
		return ResponseEntity.ok("Send direct message successfully");
	}

	@PostMapping("/group")
	public ResponseEntity<String> sendGroupMessage(@RequestBody @Valid SendGroupMessageRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		messageService.sendGroupMessage(request, jwt.getSubject());
		return ResponseEntity.ok("Send group message successfully");
	}

	@GetMapping("/{conversationId}")
	public ResponseEntity<MessagesResponse> getMessages(@PathVariable @NotNull Long conversationId,
			@RequestParam(name = "limit", defaultValue = "50") int limit,
			@RequestParam(name = "cursor", required = false) Instant cursor) {
		return ResponseEntity.ok(messageService.getMessages(conversationId, limit, cursor));
	}
}
