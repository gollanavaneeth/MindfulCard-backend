package com.mindfulcart.assistant.service;

import com.mindfulcart.assistant.model.User;
import com.mindfulcart.assistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
  private final UserRepository users;

  public User get() {
    return users
        .findByEmailIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName())
        .orElseThrow();
  }
}
