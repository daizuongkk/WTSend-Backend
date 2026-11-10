package com.wtsend.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UpdateUserRequestTest {

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

  private static Set<ConstraintViolation<UpdateUserRequest>> validate(
      UpdateUserRequest request) {
    return validator.validate(request);
  }

  @Test
  void anEmptyRequestIsValid() {
    assertThat(validate(new UpdateUserRequest(
        null, null, null, null, null, null, null))).isEmpty();
  }

  @Test
  void changingOnlyTheBioIsValid() {
    assertThat(validate(new UpdateUserRequest(
        null, null, null, null, null, "just the bio", null))).isEmpty();
  }

  @Test
  void aBlankDisplayNameIsRejected() {
    assertThat(validate(new UpdateUserRequest(
        null, null, "   ", null, null, null, null)))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("displayName");
  }

  @Test
  void thereIsNoEmailFieldToUpdate() {
    assertThat(Arrays.stream(UpdateUserRequest.class.getRecordComponents())
        .map(RecordComponent::getName))
        .doesNotContain("email");
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "25/12/1998" })
  void aBirthdayCanBeSetOrCleared(String birthday) {
    assertThat(validate(new UpdateUserRequest(
        null, null, null, birthday, null, null, null))).isEmpty();
  }

  @Test
  void aMalformedBirthdayIsStillRejected() {
    assertThat(validate(new UpdateUserRequest(
        null, null, null, "1998-12-25", null, null, null))).isNotEmpty();
  }

  @Test
  void aTooShortUsernameIsStillRejected() {
    assertThat(validate(new UpdateUserRequest(
        "short", null, null, null, null, null, null)))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("username");
  }
}