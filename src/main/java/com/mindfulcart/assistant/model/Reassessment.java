package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reassessment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private Evaluation evaluation;

  private boolean stillWantIt;
  private boolean needChanged;
  private boolean priceChanged;
  private boolean foundAlternative;
  private boolean fitsBudgetNow;
  private int updatedScore;

  @Column(length = 1000)
  private String reflection;

  @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}
