package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "app_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppNotification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false, length = 40)
  private String type;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(nullable = false, length = 1200)
  private String message;

  @Column(length = 500)
  private String actionUrl;

  @Builder.Default
  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Builder.Default
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  private LocalDateTime readAt;
}
