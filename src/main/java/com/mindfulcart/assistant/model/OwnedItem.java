package com.mindfulcart.assistant.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnedItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false)
  private String productName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Enums.Category category;

  private BigDecimal purchasePrice;
  private LocalDate purchaseDate;
  private String conditionName;
  private String usageFrequency;
}
