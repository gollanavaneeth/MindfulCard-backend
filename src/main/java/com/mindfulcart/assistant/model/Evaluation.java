package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false)
  private String productName;

  @Enumerated(EnumType.STRING)
  private Enums.Category category;

  @Column(nullable = false)
  private BigDecimal price;

  @Column(length = 1200)
  private String description;

  private String sourceOfInterest;
  private boolean discountAvailable;
  private Integer discountPercentage;
  private boolean genuinelyNeeded;
  private boolean previouslyPlanned;
  private String considerationPeriod;
  private boolean ownsSimilar;
  private boolean withinBudget;
  private boolean advertisementTriggered;
  private boolean discountTriggered;
  private boolean offerUrgency;

  @Enumerated(EnumType.STRING)
  private Enums.EmotionalState emotionalState;

  private boolean canWait;
  private boolean frequentlyUsed;
  private boolean cheaperAlternative;
  private int impulseScore;

  @Enumerated(EnumType.STRING)
  private Enums.RiskLevel riskLevel;

  @Column(length = 2000)
  private String reasons;

  private String recommendation;
  private String waitingPeriod;
  private boolean avoided;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Enums.PurchaseDecision decision = Enums.PurchaseDecision.PENDING;

  private LocalDateTime decidedAt;
  @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}
