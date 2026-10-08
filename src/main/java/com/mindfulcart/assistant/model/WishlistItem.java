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
public class WishlistItem {
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

  @Builder.Default private LocalDateTime addedDate = LocalDateTime.now();
  private LocalDateTime waitingUntil;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Enums.WishlistStatus status = Enums.WishlistStatus.WAITING;
}
