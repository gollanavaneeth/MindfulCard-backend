package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "monthly_budget_snapshots",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_budget_snapshot_user_month",
            columnNames = {"user_id", "snapshot_year", "snapshot_month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBudgetSnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(name = "snapshot_year", nullable = false)
  private int snapshotYear;

  @Column(name = "snapshot_month", nullable = false)
  private int snapshotMonth;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal discretionaryBudget;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal purchasedAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal avoidedAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal pendingAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal evaluatedAmount;

  @Column(nullable = false)
  private int purchaseCount;

  @Column(nullable = false)
  private int avoidedCount;

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime generatedAt = LocalDateTime.now();
}
