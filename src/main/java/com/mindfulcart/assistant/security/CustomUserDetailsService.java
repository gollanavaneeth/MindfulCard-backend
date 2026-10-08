package com.mindfulcart.assistant.security;

import com.mindfulcart.assistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
  private final UserRepository users;

  public UserDetails loadUserByUsername(String email) {
    var user =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    String role =
        user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole().toUpperCase();
    return User.withUsername(user.getEmail()).password(user.getPassword()).roles(role).build();
  }
}
