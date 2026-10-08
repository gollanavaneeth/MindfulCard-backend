package com.mindfulcart.assistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false, unique = true)
  private String email;

  @JsonIgnore
  @Column(nullable = false)
  private String password;

  @Builder.Default
  @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'USER'")
  private String role = "USER";

  @Builder.Default private boolean emailVerified = false;
  @Builder.Default private boolean twoFactorEnabled = false;
  private String preferredCurrency;

  @Builder.Default
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
