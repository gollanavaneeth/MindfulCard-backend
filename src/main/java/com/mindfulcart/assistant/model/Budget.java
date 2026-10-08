package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false)
  private User user;

  @Column(nullable = false)
  private BigDecimal monthlyIncome;

  @Column(nullable = false)
  private BigDecimal essentialExpenses;

  @Column(nullable = false)
  private BigDecimal savingsTarget;

  public BigDecimal getDiscretionaryBudget() {
    return monthlyIncome.subtract(essentialExpenses).subtract(savingsTarget).max(BigDecimal.ZERO);
  }
}
