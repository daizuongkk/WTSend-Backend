package com.wtsend.backend.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.dto.response.UserSummaryResponse;
import com.wtsend.backend.libs.RefreshTokenCookie;
import com.wtsend.backend.services.interfaces.IAuthService;
import com.wtsend.backend.services.interfaces.IUserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
public class UserController {
  private final IUserService userService;
  private final IAuthService authService;

  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(userService.findById(jwt.getSubject()));
  }

  @GetMapping("/search")
  public ResponseEntity<UserSummaryResponse> searchUserByUsername(
      @RequestParam(name = "username") @NotBlank(message = "Username is required") String username) {
    return ResponseEntity.ok(userService.findByUsername(username));
  }

  /**
   * Uploads an avatar on its own. The web client does not use this -- it sends
   * the file inside {@code PUT /api/users} -- but a non-web client does, so it
   * is not dead code despite having no caller in this repository.
   */
  @PostMapping("/upload")
  public ResponseEntity<Map<String, String>> uploadAvatar(@RequestParam(name = "img") MultipartFile file,
      @AuthenticationPrincipal Jwt jwt) {
    String imgUrl = userService.uploadAvatar(file, jwt.getSubject());

    return ResponseEntity.ok(Map.of("avatarUrl", imgUrl));
  }

  @PutMapping("/password")
  /**
   * Returns a new token pair rather than 204. Changing the password revokes
   * every refresh token, including this device's, so a 204 left the client
   * working until its access token expired and then dropped it at the login
   * screen with no explanation.
   */
  public ResponseEntity<AuthResponse> changePassword(@RequestBody @Valid ChangePasswordRequest request,
      @AuthenticationPrincipal Jwt jwt) {

    AuthResponse res = authService.changePassword(request, jwt.getSubject());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.of(res.getRefreshToken()).toString())
        .body(res);
  }

  @PutMapping("")
  public ResponseEntity<UserResponse> updateUser(@ModelAttribute @Valid UpdateUserRequest request,
      @AuthenticationPrincipal Jwt jwt) {

    return ResponseEntity.ok(userService.updateUser(request, jwt.getSubject()));
  }

}
