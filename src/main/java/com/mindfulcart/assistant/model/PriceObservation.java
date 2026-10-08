package com.mindfulcart.assistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "price_observation",
    indexes = @Index(name = "idx_price_observation_watch_time", columnList = "watch_id,observedAt"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceObservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private PriceWatch watch;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal price;

  @Column(length = 120)
  private String retailer;

  @Column(length = 1_000)
  private String productUrl;

  @Column(nullable = false)
  private LocalDateTime observedAt;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
