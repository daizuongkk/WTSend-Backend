package com.wtsend.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.wtsend.backend.dto.MediaRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Bean validation runs before the controller, so a constraint here can make a
 * service branch unreachable without any test in {@code MessageServiceTest}
 * noticing -- those call the service directly and never see the validator.
 */
class SendDirectMessageRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void openValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private static SendDirectMessageRequest request(
      String recipientId,
      Long conversationId,
      String content) {
    return SendDirectMessageRequest.builder()
        .recipientId(recipientId)
        .conversationId(conversationId)
        .content(content)
        .build();
  }

  private static SendDirectMessageRequest mediaRequest(String recipientId, MediaRequest media) {

    return new SendDirectMessageRequest(
        recipientId,
        null,
        null,
        media);
  }

  @Test
  void aNullConversationIdIsValid() {
    // The first message to a friend carries no conversation id. @NotNull here
    // rejected it with 400, so that conversation could never be started at all.
    Set<ConstraintViolation<SendDirectMessageRequest>> violations = validator
        .validate(request("bob-id", null, "hi"));

    assertThat(violations).isEmpty();
  }

  @Test
  void anMediaOnlyMessageIsValid() {

    // content was @NotBlank, so a message that is just an attached image was
    // rejected -- yet imgUrl was accepted by the DTO and then never read.
    assertThat(validator.validate(mediaRequest("bob-id", new MediaRequest("png", "dz.png", "aaaaa.vn")))).isEmpty();
  }

  @Test
  void aMessageWithNeitherTextNorImageIsRejected() {
    assertThat(validator.validate(request("bob-id", null, "   ")))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("textOrImagePresent");
  }

  @Test
  void aRecipientIdIsStillRequired() {
    assertThat(validator.validate(request("  ", null, "hi")))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("recipientId");
  }
}
