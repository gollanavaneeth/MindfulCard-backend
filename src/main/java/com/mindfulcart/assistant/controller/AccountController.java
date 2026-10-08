package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AccountController {
  private final UserRepository userRepository;
  private final AccountTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final CurrentUserService currentUserService;

  public record EmailRequest(@Email @NotBlank String email) {}

  public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 8) String newPassword) {}

  public record PasswordRequest(
      @NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {}

  @PostMapping("/api/auth/forgot-password")
  Map<String, Object> forgotPassword(@Valid @RequestBody EmailRequest request) {
    Optional<User> user = userRepository.findByEmailIgnoreCase(request.email());
    if (user.isEmpty()) {
      return Map.of("message", "If the email exists, reset instructions have been created.");
    }

    String token = createToken(user.get(), "RESET", 30);
    return Map.of(
        "message",
        "Reset instructions created.",
        "developmentToken",
        token,
        "expiresInMinutes",
        30);
  }

  @PostMapping("/api/auth/reset-password")
  Map<String, String> resetPassword(@Valid @RequestBody ResetRequest request) {
    AccountToken token = validateToken(request.token(), "RESET");
    token.getUser().setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(token.getUser());
    token.setUsed(true);
    tokenRepository.save(token);
    return Map.of("message", "Password reset successfully");
  }

  @PostMapping("/api/auth/request-verification")
  Map<String, Object> requestVerification(@Valid @RequestBody EmailRequest request) {
    User user =
        userRepository
            .findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    String token = createToken(user, "VERIFY", 1440);
    return Map.of("message", "Verification created.", "developmentToken", token);
  }

  @PostMapping("/api/auth/verify-email")
  Map<String, String> verifyEmail(@RequestBody Map<String, String> request) {
    AccountToken token = validateToken(request.get("token"), "VERIFY");
    token.getUser().setEmailVerified(true);
    userRepository.save(token.getUser());
    token.setUsed(true);
    tokenRepository.save(token);
    return Map.of("message", "Email verified");
  }

  @PutMapping("/api/account/password")
  Map<String, String> changePassword(@Valid @RequestBody PasswordRequest request) {
    User user = currentUserService.get();
    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }

    user.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
    return Map.of("message", "Password changed");
  }

  private String createToken(User user, String type, int minutes) {
    String value = UUID.randomUUID().toString();
    tokenRepository.save(
        AccountToken.builder()
            .user(user)
            .token(value)
            .type(type)
            .expiresAt(LocalDateTime.now().plusMinutes(minutes))
            .build());
    return value;
  }

  private AccountToken validateToken(String value, String type) {
    if (value == null) {
      throw new IllegalArgumentException("Token is required");
    }

    AccountToken token =
        tokenRepository
            .findByTokenAndTypeAndUsedFalse(value, type)
            .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
    if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new IllegalArgumentException("Token has expired");
    }
    return token;
  }
}
