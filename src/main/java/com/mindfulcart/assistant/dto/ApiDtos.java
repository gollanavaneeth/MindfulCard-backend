package com.mindfulcart.assistant.dto;

import com.mindfulcart.assistant.model.Enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ApiDtos {
  private ApiDtos() {}

  public record Register(
      @NotBlank String fullName, @Email @NotBlank String email, @Size(min = 8) String password) {}

  public record Login(@Email @NotBlank String email, @NotBlank String password) {}

  public record AuthResponse(String token, Long id, String fullName, String email, String role) {}

  public record Profile(
      Long id, String fullName, String email, String role, LocalDateTime createdAt) {}

  public record ProfileUpdate(@NotBlank String fullName) {}

  public record EvaluationRequest(
      @NotBlank String productName,
      @NotNull Category category,
      @NotNull @Positive BigDecimal price,
      String description,
      String sourceOfInterest,
      boolean discountAvailable,
      Integer discountPercentage,
      boolean genuinelyNeeded,
      boolean previouslyPlanned,
      String considerationPeriod,
      boolean ownsSimilar,
      boolean withinBudget,
      boolean advertisementTriggered,
      boolean discountTriggered,
      boolean offerUrgency,
      @NotNull EmotionalState emotionalState,
      boolean canWait,
      boolean frequentlyUsed,
      boolean cheaperAlternative) {}

  public record EvaluationResponse(
      Long id,
      String productName,
      Category category,
      BigDecimal price,
      int impulseScore,
      RiskLevel riskLevel,
      List<String> reasons,
      String recommendation,
      String waitingPeriod,
      long similarOwnedCount,
      String similarItemAlert,
      PurchaseDecision decision,
      LocalDateTime decidedAt,
      LocalDateTime createdAt) {}

  public record OwnedItemRequest(
      @NotBlank String productName,
      @NotNull Category category,
      BigDecimal purchasePrice,
      LocalDate purchaseDate,
      String conditionName,
      String usageFrequency) {}

  public record OwnedItemResponse(
      Long id,
      String productName,
      Category category,
      BigDecimal purchasePrice,
      LocalDate purchaseDate,
      String conditionName,
      String usageFrequency) {}

  public record BudgetRequest(
      @NotNull @PositiveOrZero BigDecimal monthlyIncome,
      @NotNull @PositiveOrZero BigDecimal essentialExpenses,
      @NotNull @PositiveOrZero BigDecimal savingsTarget) {}

  public record BudgetResponse(
      Long id,
      BigDecimal monthlyIncome,
      BigDecimal essentialExpenses,
      BigDecimal savingsTarget,
      BigDecimal discretionaryBudget,
      BigDecimal evaluatedThisMonth,
      BigDecimal purchasedThisMonth,
      BigDecimal pendingExposure,
      BigDecimal remainingBudget,
      String alert) {}

  public record WishlistRequest(
      @NotBlank String productName,
      @NotNull Category category,
      @NotNull @Positive BigDecimal price,
      LocalDateTime waitingUntil,
      WishlistStatus status) {}

  public record WishlistResponse(
      Long id,
      String productName,
      Category category,
      BigDecimal price,
      LocalDateTime addedDate,
      LocalDateTime waitingUntil,
      WishlistStatus status) {}

  public record GoalRequest(
      @NotBlank String title,
      @NotNull @Positive BigDecimal targetAmount,
      @PositiveOrZero BigDecimal manuallySaved,
      LocalDate targetDate) {}

  public record GoalResponse(
      Long id,
      String title,
      BigDecimal targetAmount,
      BigDecimal manuallySaved,
      BigDecimal impulseSavings,
      BigDecimal totalSaved,
      double progress,
      LocalDate targetDate) {}
}
