package com.wtsend.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dtos.request.CreateConversationRequest;
import com.wtsend.backend.dtos.response.ConversationResponse;
import com.wtsend.backend.services.interfaces.IConversationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

	private final IConversationService conversationService;

	ConversationController(IConversationService conversationService) {
		this.conversationService = conversationService;
	}

	@GetMapping("")
	public ResponseEntity<Object> getConversation(@AuthenticationPrincipal Jwt jwt) {
		var response = conversationService.getConversations(jwt.getSubject());
		return ResponseEntity.ok(response);
	}

	@PostMapping()
	public ResponseEntity<ConversationResponse> createConversation(@RequestBody @Valid CreateConversationRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(conversationService.createConversation(request, jwt.getSubject()));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<String> markAsSeen(@PathVariable(name = "id") @NotNull Long conversationId,
			@AuthenticationPrincipal Jwt jwt) {
		conversationService.markAsSeen(conversationId, jwt.getSubject());

		return ResponseEntity.ok("mark as seen thành công");
	}

}
