package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.User;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByRoleIgnoreCase(String role);

  long countByRoleIgnoreCase(String role);

  List<User> findAllByOrderByCreatedAtDesc();
}
