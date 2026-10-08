package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsGoal {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private BigDecimal targetAmount;

  @Builder.Default private BigDecimal manuallySaved = BigDecimal.ZERO;
  private LocalDate targetDate;
  @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}
