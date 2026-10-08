package com.mindfulcart.assistant.config;

import com.mindfulcart.assistant.model.User;
import com.mindfulcart.assistant.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {
  private final UserRepository users;

  @Override
  @Transactional
  public void run(String... args) {
    var all = users.findAll();
    all.stream()
        .filter(user -> user.getRole() == null || user.getRole().isBlank())
        .forEach(user -> user.setRole("USER"));
    if (!all.isEmpty()
        && all.stream().noneMatch(user -> "ADMIN".equalsIgnoreCase(user.getRole()))) {
      User first =
          all.stream()
              .min(
                  Comparator.comparing(
                      user ->
                          user.getCreatedAt() == null ? LocalDateTime.MAX : user.getCreatedAt()))
              .orElseThrow();
      first.setRole("ADMIN");
    }
    users.saveAll(all);
  }
}
