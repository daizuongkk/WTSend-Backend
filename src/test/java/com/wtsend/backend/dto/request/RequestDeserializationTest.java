package com.wtsend.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.corundumstudio.socketio.SocketIOServer;

import tools.jackson.databind.ObjectMapper;

/**
 * Every request DTO, read with the mapper the controllers actually use.
 *
 * <p>
 * This is not a test of Jackson. Boot 4 brought Jackson 3, which does not
 * accept a Lombok {@code @Builder} class as a target the way Jackson 2 did --
 * it found the generated all-args constructor through parameter names, and
 * Jackson 3 requires a creator it can see. The result was a 500 on
 * {@code /api/auth/register} and {@code /api/auth/login}, and a request body
 * silently read as all-null on the DTOs that have a default constructor and no
 * setters. Nothing in the suite noticed, because validation tests build their
 * DTOs in Java rather than from JSON.
 *
 * <p>
 * So: assert for each DTO that a field put into the JSON comes back out of the
 * object. Anything less -- just "it deserialises" -- would have passed while
 * every field was null.
 */
@SpringBootTest
class RequestDeserializationTest {

	/** Keeps the real server off port 9092 while this context is up. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private ObjectMapper json;

	@Test
	void signUpRequestReadsItsFields() {
		SignUpRequest request = json.readValue(
				"""
						{"email":"a@example.com","password":"Abcdef1!"}""",
				SignUpRequest.class);

		assertThat(request.getEmail()).isEqualTo("a@example.com");
		assertThat(request.getPassword()).isEqualTo("Abcdef1!");
	}

	@Test
	void signInRequestReadsItsFields() {
		SignInRequest request = json.readValue(
				"""
						{"email":"a@example.com","password":"Abcdef1!"}""",
				SignInRequest.class);

		assertThat(request.getEmail()).isEqualTo("a@example.com");
		assertThat(request.getPassword()).isEqualTo("Abcdef1!");
	}

	@Test
	void sendDirectMessageRequestReadsItsFields() {
		SendDirectMessageRequest request = json.readValue(
				"""
						{"recipientId":"u-1","conversationId":7,"content":"xin chào","imgUrl":"https://x/y.png"}""",
				SendDirectMessageRequest.class);

		assertThat(request.getRecipientId()).isEqualTo("u-1");
		assertThat(request.getConversationId()).isEqualTo(7L);
		assertThat(request.getContent()).isEqualTo("xin chào");
		assertThat(request.getImgUrl()).isEqualTo("https://x/y.png");
	}

	@Test
	void sendGroupMessageRequestReadsItsFields() {
		SendGroupMessageRequest request = json.readValue(
				"""
						{"conversationId":7,"content":"xin chào","imgUrl":"https://x/y.png"}""",
				SendGroupMessageRequest.class);

		assertThat(request.getConversationId()).isEqualTo(7L);
		assertThat(request.getContent()).isEqualTo("xin chào");
		assertThat(request.getImgUrl()).isEqualTo("https://x/y.png");
	}

	@Test
	void createConversationRequestReadsItsFields() {
		CreateConversationRequest request = json.readValue(
				"""
						{"type":"GROUP","name":"nhóm 1","memberIds":["u-1","u-2"]}""",
				CreateConversationRequest.class);

		assertThat(request.getName()).isEqualTo("nhóm 1");
		assertThat(request.getMemberIds()).containsExactly("u-1", "u-2");
	}

	@Test
	void updateUserRequestReadsItsFields() {
		UpdateUserRequest request = json.readValue(
				"""
						{"username":"someone1","displayName":"Ai Đó"}""",
				UpdateUserRequest.class);

		assertThat(request.getUsername()).isEqualTo("someone1");
		assertThat(request.getDisplayName()).isEqualTo("Ai Đó");
	}

	@Test
	void addFriendRequestReadsItsFields() {
		AddFriendRequest request = json.readValue(
				"""
						{"to":"u-2","message":"kết bạn nhé"}""",
				AddFriendRequest.class);

		assertThat(request.getTo()).isEqualTo("u-2");
		assertThat(request.getMessage()).isEqualTo("kết bạn nhé");
	}

	@Test
	void changePasswordRequestReadsItsFields() {
		ChangePasswordRequest request = json.readValue(
				"""
						{"currentPassword":"Abcdef1!","newPassword":"Bcdefg2@"}""",
				ChangePasswordRequest.class);

		assertThat(request.getCurrentPassword()).isEqualTo("Abcdef1!");
		assertThat(request.getNewPassword()).isEqualTo("Bcdefg2@");
	}

	@Test
	void forgotAndResetPasswordRequestsReadTheirFields() {
		assertThat(json.readValue("""
				{"email":"a@example.com"}""", ForgotPasswordRequest.class).getEmail())
				.isEqualTo("a@example.com");

		ResetPasswordRequest reset = json.readValue("""
				{"token":"t-1","newPassword":"Bcdefg2@"}""", ResetPasswordRequest.class);
		assertThat(reset.getToken()).isEqualTo("t-1");
		assertThat(reset.getNewPassword()).isEqualTo("Bcdefg2@");
	}

	@Test
	void oauthRequestsReadTheirFields() {
		assertThat(json.readValue("""
				{"code":"auth-code"}""", GoogleAuthRequest.class).getCode())
				.isEqualTo("auth-code");

		assertThat(json.readValue("""
				{"accessToken":"fb-token"}""", FacebookAuthRequest.class).getAccessToken())
				.isEqualTo("fb-token");
	}
}
