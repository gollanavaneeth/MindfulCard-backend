package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "shopping_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoppingRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 600)
  private String description;

  @Column(nullable = false, length = 40)
  private String ruleType;

  @Column(length = 40)
  private String category;

  @Column(precision = 12, scale = 2)
  private BigDecimal amountThreshold;

  private Integer waitDays;

  @Column(length = 40)
  private String blockedEmotion;

  private Integer startHour;
  private Integer endHour;

  @Builder.Default
  @Column(nullable = false)
  private boolean active = true;

  @Builder.Default
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
