package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.dto.ApiDtos.*;
import com.mindfulcart.assistant.model.User;
import com.mindfulcart.assistant.repository.UserRepository;
import com.mindfulcart.assistant.security.JwtService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  @PostMapping("/register")
  ResponseEntity<?> register(@Valid @RequestBody Register request) {
    String email = request.email().trim().toLowerCase();
    if (userRepository.existsByEmailIgnoreCase(email)) {
      return ResponseEntity.status(409).body(Map.of("message", "Email is already registered"));
    }

    String role = userRepository.count() == 0 ? "ADMIN" : "USER";
    User user =
        userRepository.save(
            User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .build());
    return ResponseEntity.status(201).body(createAuthResponse(user));
  }

  @PostMapping("/login")
  AuthResponse login(@Valid @RequestBody Login request) {
    String email = request.email().trim().toLowerCase();
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(email, request.password()));
    User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
    return createAuthResponse(user);
  }

  private AuthResponse createAuthResponse(User user) {
    String role =
        user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole().toUpperCase();
    return new AuthResponse(
        jwtService.generate(userDetailsService.loadUserByUsername(user.getEmail())),
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        role);
  }
}
