package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budget-history")
@RequiredArgsConstructor
public class BudgetHistoryController {
  private final MonthlyBudgetSnapshotRepository snapshots;
  private final EvaluationRepository evaluations;
  private final BudgetRepository budgets;
  private final CurrentUserService current;

  public record SnapshotRequest(
      @NotNull @Min(2000) @Max(2200) Integer year, @NotNull @Min(1) @Max(12) Integer month) {}

  public record SnapshotResponse(
      Long id,
      int year,
      int month,
      String monthLabel,
      BigDecimal discretionaryBudget,
      BigDecimal purchasedAmount,
      BigDecimal avoidedAmount,
      BigDecimal pendingAmount,
      BigDecimal evaluatedAmount,
      BigDecimal remainingBudget,
      int purchaseCount,
      int avoidedCount,
      LocalDateTime generatedAt) {}

  @GetMapping
  public List<SnapshotResponse> all(
      @RequestParam(defaultValue = "12") @Min(1) @Max(60) int months) {
    return snapshots
        .findByUserIdOrderBySnapshotYearDescSnapshotMonthDesc(current.get().getId())
        .stream()
        .limit(months)
        .map(this::dto)
        .toList();
  }

  @GetMapping("/current")
  public SnapshotResponse currentMonth() {
    MonthlyBudgetSnapshot calculated = calculate(YearMonth.now());
    return dto(calculated);
  }

  @PostMapping("/snapshot/current")
  public SnapshotResponse snapshotCurrent() {
    return save(YearMonth.now());
  }

  @PostMapping("/snapshot")
  public SnapshotResponse snapshot(@Valid @RequestBody SnapshotRequest request) {
    YearMonth month = YearMonth.of(request.year(), request.month());
    if (month.isAfter(YearMonth.now()))
      throw new IllegalArgumentException("A future month cannot be snapshotted");
    return save(month);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    snapshots.delete(mine(id));
  }

  private SnapshotResponse save(YearMonth month) {
    Long userId = current.get().getId();
    MonthlyBudgetSnapshot calculated = calculate(month);
    MonthlyBudgetSnapshot snapshot =
        snapshots
            .findByUserIdAndSnapshotYearAndSnapshotMonth(
                userId, month.getYear(), month.getMonthValue())
            .orElseGet(MonthlyBudgetSnapshot::new);
    snapshot.setUser(current.get());
    snapshot.setSnapshotYear(calculated.getSnapshotYear());
    snapshot.setSnapshotMonth(calculated.getSnapshotMonth());
    snapshot.setDiscretionaryBudget(calculated.getDiscretionaryBudget());
    snapshot.setPurchasedAmount(calculated.getPurchasedAmount());
    snapshot.setAvoidedAmount(calculated.getAvoidedAmount());
    snapshot.setPendingAmount(calculated.getPendingAmount());
    snapshot.setEvaluatedAmount(calculated.getEvaluatedAmount());
    snapshot.setPurchaseCount(calculated.getPurchaseCount());
    snapshot.setAvoidedCount(calculated.getAvoidedCount());
    snapshot.setGeneratedAt(LocalDateTime.now());
    return dto(snapshots.save(snapshot));
  }

  private MonthlyBudgetSnapshot calculate(YearMonth month) {
    Long userId = current.get().getId();
    List<Evaluation> items =
        evaluations.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(e -> e.getCreatedAt() != null && YearMonth.from(e.getCreatedAt()).equals(month))
            .toList();
    BigDecimal evaluated =
        items.stream().map(Evaluation::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal purchased = amount(items, Enums.PurchaseDecision.PURCHASED);
    BigDecimal avoided = amount(items, Enums.PurchaseDecision.AVOIDED);
    BigDecimal pending =
        items.stream()
            .filter(
                e ->
                    e.getDecision() == Enums.PurchaseDecision.PENDING
                        || e.getDecision() == Enums.PurchaseDecision.WAITING)
            .map(Evaluation::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal discretionary =
        budgets.findByUserId(userId).map(Budget::getDiscretionaryBudget).orElse(BigDecimal.ZERO);

    return MonthlyBudgetSnapshot.builder()
        .user(current.get())
        .snapshotYear(month.getYear())
        .snapshotMonth(month.getMonthValue())
        .discretionaryBudget(discretionary)
        .purchasedAmount(purchased)
        .avoidedAmount(avoided)
        .pendingAmount(pending)
        .evaluatedAmount(evaluated)
        .purchaseCount(
            (int)
                items.stream()
                    .filter(e -> e.getDecision() == Enums.PurchaseDecision.PURCHASED)
                    .count())
        .avoidedCount(
            (int)
                items.stream()
                    .filter(e -> e.getDecision() == Enums.PurchaseDecision.AVOIDED)
                    .count())
        .generatedAt(LocalDateTime.now())
        .build();
  }

  private BigDecimal amount(List<Evaluation> items, Enums.PurchaseDecision decision) {
    return items.stream()
        .filter(e -> e.getDecision() == decision)
        .map(Evaluation::getPrice)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private SnapshotResponse dto(MonthlyBudgetSnapshot snapshot) {
    String label =
        Month.of(snapshot.getSnapshotMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            + " "
            + snapshot.getSnapshotYear();
    return new SnapshotResponse(
        snapshot.getId(),
        snapshot.getSnapshotYear(),
        snapshot.getSnapshotMonth(),
        label,
        snapshot.getDiscretionaryBudget(),
        snapshot.getPurchasedAmount(),
        snapshot.getAvoidedAmount(),
        snapshot.getPendingAmount(),
        snapshot.getEvaluatedAmount(),
        snapshot.getDiscretionaryBudget().subtract(snapshot.getPurchasedAmount()),
        snapshot.getPurchaseCount(),
        snapshot.getAvoidedCount(),
        snapshot.getGeneratedAt());
  }

  private MonthlyBudgetSnapshot mine(Long id) {
    MonthlyBudgetSnapshot snapshot =
        snapshots
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Budget snapshot not found"));
    if (!snapshot.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Budget snapshot not found");
    return snapshot;
  }
}
