package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "category_budgets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_category_budget_user_category",
            columnNames = {"user_id", "category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryBudget {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false, length = 50)
  private String category;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal monthlyLimit;

  @Builder.Default
  @Column(nullable = false)
  private int warningThreshold = 80;

  @Builder.Default
  @Column(nullable = false)
  private boolean active = true;

  @Builder.Default
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime updatedAt = LocalDateTime.now();

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
