package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 60)
  private String category;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 20)
  private String billingCycle;

  private LocalDate nextRenewalDate;
  private LocalDate lastUsedDate;

  @Builder.Default
  @Column(nullable = false)
  private boolean autoRenew = true;

  @Builder.Default
  @Column(nullable = false)
  private boolean active = true;

  @Column(length = 1000)
  private String notes;

  @Builder.Default
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
