package com.mindfulcart.assistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "price_watch",
    indexes = @Index(name = "idx_price_watch_user_active", columnList = "user_id,active"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceWatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false, length = 160)
  private String productName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  @Builder.Default
  private Enums.Category category = Enums.Category.OTHER;

  @Column(length = 1_000)
  private String productUrl;

  @Column(length = 120)
  private String retailer;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal targetPrice;

  @Column(precision = 14, scale = 2)
  private BigDecimal currentPrice;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  private LocalDateTime lastObservedAt;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();

  @JsonIgnore
  @OneToMany(mappedBy = "watch", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<PriceObservation> observations = new ArrayList<>();
}
