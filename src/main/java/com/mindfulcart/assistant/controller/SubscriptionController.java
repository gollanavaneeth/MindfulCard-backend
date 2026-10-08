package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.Subscription;
import com.mindfulcart.assistant.repository.SubscriptionRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
  private static final Set<String> CYCLES = Set.of("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");
  private final SubscriptionRepository subscriptions;
  private final CurrentUserService current;

  public record SubscriptionRequest(
      @NotBlank @Size(max = 120) String name,
      @Size(max = 60) String category,
      @NotNull @PositiveOrZero BigDecimal amount,
      @NotBlank String billingCycle,
      LocalDate nextRenewalDate,
      LocalDate lastUsedDate,
      Boolean autoRenew,
      Boolean active,
      @Size(max = 1000) String notes) {}

  public record SubscriptionResponse(
      Long id,
      String name,
      String category,
      BigDecimal amount,
      String billingCycle,
      LocalDate nextRenewalDate,
      LocalDate lastUsedDate,
      boolean autoRenew,
      boolean active,
      String notes,
      BigDecimal monthlyCost,
      Long daysUntilRenewal,
      boolean renewalSoon,
      boolean unused,
      LocalDateTime createdAt) {}

  public record SubscriptionSummary(
      long activeCount,
      BigDecimal monthlyCost,
      BigDecimal annualCost,
      long renewalSoonCount,
      long unusedCount) {}

  @GetMapping
  public List<SubscriptionResponse> all() {
    return subscriptions.findByUserIdOrderByNextRenewalDateAsc(current.get().getId()).stream()
        .map(this::dto)
        .toList();
  }

  @GetMapping("/summary")
  public SubscriptionSummary summary() {
    List<SubscriptionResponse> active =
        all().stream().filter(SubscriptionResponse::active).toList();
    BigDecimal monthly =
        active.stream()
            .map(SubscriptionResponse::monthlyCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new SubscriptionSummary(
        active.size(),
        monthly,
        monthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP),
        active.stream().filter(SubscriptionResponse::renewalSoon).count(),
        active.stream().filter(SubscriptionResponse::unused).count());
  }

  @GetMapping("/renewals")
  public List<SubscriptionResponse> renewals(
      @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
    return all().stream()
        .filter(SubscriptionResponse::active)
        .filter(
            s ->
                s.daysUntilRenewal() != null
                    && s.daysUntilRenewal() >= 0
                    && s.daysUntilRenewal() <= days)
        .toList();
  }

  @PostMapping
  public SubscriptionResponse create(@Valid @RequestBody SubscriptionRequest request) {
    Subscription subscription = new Subscription();
    subscription.setUser(current.get());
    copy(subscription, request, true);
    return dto(subscriptions.save(subscription));
  }

  @PutMapping("/{id}")
  public SubscriptionResponse update(
      @PathVariable Long id, @Valid @RequestBody SubscriptionRequest request) {
    Subscription subscription = mine(id);
    copy(subscription, request, false);
    return dto(subscriptions.save(subscription));
  }

  @PutMapping("/{id}/used")
  public SubscriptionResponse markUsed(@PathVariable Long id) {
    Subscription subscription = mine(id);
    subscription.setLastUsedDate(LocalDate.now());
    return dto(subscriptions.save(subscription));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    subscriptions.delete(mine(id));
  }

  private void copy(Subscription subscription, SubscriptionRequest request, boolean creating) {
    String cycle = normalize(request.billingCycle());
    if (!CYCLES.contains(cycle)) throw new IllegalArgumentException("Unsupported billing cycle");
    subscription.setName(request.name().trim());
    subscription.setCategory(blank(request.category()) ? null : request.category().trim());
    subscription.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
    subscription.setBillingCycle(cycle);
    subscription.setNextRenewalDate(request.nextRenewalDate());
    subscription.setLastUsedDate(request.lastUsedDate());
    subscription.setAutoRenew(
        request.autoRenew() == null ? creating || subscription.isAutoRenew() : request.autoRenew());
    subscription.setActive(
        request.active() == null ? creating || subscription.isActive() : request.active());
    subscription.setNotes(blank(request.notes()) ? null : request.notes().trim());
  }

  private SubscriptionResponse dto(Subscription subscription) {
    LocalDate today = LocalDate.now();
    Long days =
        subscription.getNextRenewalDate() == null
            ? null
            : ChronoUnit.DAYS.between(today, subscription.getNextRenewalDate());
    boolean soon = subscription.isActive() && days != null && days >= 0 && days <= 7;
    boolean unused =
        subscription.isActive()
            && (subscription.getLastUsedDate() == null
                || subscription.getLastUsedDate().isBefore(today.minusDays(45)));
    return new SubscriptionResponse(
        subscription.getId(),
        subscription.getName(),
        subscription.getCategory(),
        subscription.getAmount(),
        subscription.getBillingCycle(),
        subscription.getNextRenewalDate(),
        subscription.getLastUsedDate(),
        subscription.isAutoRenew(),
        subscription.isActive(),
        subscription.getNotes(),
        monthlyCost(subscription),
        days,
        soon,
        unused,
        subscription.getCreatedAt());
  }

  private BigDecimal monthlyCost(Subscription subscription) {
    BigDecimal amount = subscription.getAmount();
    return switch (subscription.getBillingCycle()) {
      case "WEEKLY" ->
          amount
              .multiply(BigDecimal.valueOf(52))
              .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
      case "QUARTERLY" -> amount.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
      case "YEARLY" -> amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
      default -> amount.setScale(2, RoundingMode.HALF_UP);
    };
  }

  private Subscription mine(Long id) {
    Subscription subscription =
        subscriptions
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
    if (!subscription.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Subscription not found");
    return subscription;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }
}
