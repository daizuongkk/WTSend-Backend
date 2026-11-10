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

    assertThat(request.email()).isEqualTo("a@example.com");
    assertThat(request.password()).isEqualTo("Abcdef1!");
  }

  @Test
  void signInRequestReadsItsFields() {
    SignInRequest request = json.readValue(
        """
            {"email":"a@example.com","password":"Abcdef1!"}""",
        SignInRequest.class);

    assertThat(request.email()).isEqualTo("a@example.com");
    assertThat(request.password()).isEqualTo("Abcdef1!");
  }

  @Test
  void sendDirectMessageRequestReadsItsFields() {
    SendDirectMessageRequest request = json.readValue(
        """
            {"recipientId":"u-1","conversationId":7,"content":"xin chào","imgUrl":"https://x/y.png"}""",
        SendDirectMessageRequest.class);

    assertThat(request.recipientId()).isEqualTo("u-1");
    assertThat(request.conversationId()).isEqualTo(7L);
    assertThat(request.content()).isEqualTo("xin chào");
  }

  @Test
  void sendGroupMessageRequestReadsItsFields() {
    SendGroupMessageRequest request = json.readValue(
        """
            {"conversationId":7,"content":"xin chào","imgUrl":"https://x/y.png"}""",
        SendGroupMessageRequest.class);

    assertThat(request.conversationId()).isEqualTo(7L);
    assertThat(request.content()).isEqualTo("xin chào");
  }

  @Test
  void createConversationRequestReadsItsFields() {
    CreateConversationRequest request = json.readValue(
        """
            {"type":"GROUP","name":"nhóm 1","memberIds":["u-1","u-2"]}""",
        CreateConversationRequest.class);

    assertThat(request.name()).isEqualTo("nhóm 1");
    assertThat(request.memberIds()).containsExactly("u-1", "u-2");
  }

  @Test
  void updateUserRequestReadsItsFields() {
    UpdateUserRequest request = json.readValue(
        """
            {"username":"someone1","displayName":"Ai Đó"}""",
        UpdateUserRequest.class);

    assertThat(request.username()).isEqualTo("someone1");
    assertThat(request.displayName()).isEqualTo("Ai Đó");
  }

  @Test
  void addFriendRequestReadsItsFields() {
    AddFriendRequest request = json.readValue(
        """
            {"to":"u-2","message":"kết bạn nhé"}""",
        AddFriendRequest.class);

    assertThat(request.to()).isEqualTo("u-2");
    assertThat(request.message()).isEqualTo("kết bạn nhé");
  }

  @Test
  void changePasswordRequestReadsItsFields() {
    ChangePasswordRequest request = json.readValue(
        """
            {"currentPassword":"Abcdef1!","newPassword":"Bcdefg2@"}""",
        ChangePasswordRequest.class);

    assertThat(request.currentPassword()).isEqualTo("Abcdef1!");
    assertThat(request.newPassword()).isEqualTo("Bcdefg2@");
  }

  @Test
  void forgotAndResetPasswordRequestsReadTheirFields() {
    assertThat(json.readValue("""
        {"email":"a@example.com"}""", ForgotPasswordRequest.class).email())
        .isEqualTo("a@example.com");

    ResetPasswordRequest reset = json.readValue("""
        {"token":"t-1","newPassword":"Bcdefg2@"}""", ResetPasswordRequest.class);
    assertThat(reset.token()).isEqualTo("t-1");
    assertThat(reset.newPassword()).isEqualTo("Bcdefg2@");
  }

  @Test
  void oauthRequestsReadTheirFields() {
    assertThat(json.readValue("""
        {"code":"auth-code"}""", GoogleAuthRequest.class).code())
        .isEqualTo("auth-code");

    assertThat(json.readValue("""
        {"accessToken":"fb-token"}""", FacebookAuthRequest.class).accessToken())
        .isEqualTo("fb-token");
  }
}
