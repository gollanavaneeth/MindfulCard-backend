package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.dto.ApiDtos.*;
import com.mindfulcart.assistant.repository.UserRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
  private final CurrentUserService current;
  private final UserRepository users;

  @GetMapping("/profile")
  Profile get() {
    var u = current.get();
    return new Profile(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getCreatedAt());
  }

  @PutMapping("/profile")
  Profile update(@Valid @RequestBody ProfileUpdate r) {
    var u = current.get();
    u.setFullName(r.fullName().trim());
    u = users.save(u);
    return new Profile(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getCreatedAt());
  }
}
