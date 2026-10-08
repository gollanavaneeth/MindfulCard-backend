package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/category-budgets")
@RequiredArgsConstructor
public class CategoryBudgetController {
  private final CategoryBudgetRepository categoryBudgets;
  private final EvaluationRepository evaluations;
  private final CurrentUserService current;

  public record CategoryBudgetRequest(
      @NotBlank @Size(max = 50) String category,
      @NotNull @Positive BigDecimal monthlyLimit,
      @Min(1) @Max(100) Integer warningThreshold,
      Boolean active) {}

  public record CategoryBudgetResponse(
      Long id,
      String category,
      BigDecimal monthlyLimit,
      int warningThreshold,
      boolean active,
      BigDecimal spent,
      BigDecimal pending,
      BigDecimal avoided,
      BigDecimal remaining,
      double usagePercent,
      String status,
      long purchaseCount,
      LocalDateTime updatedAt) {}

  public record CategoryBudgetOverview(
      BigDecimal totalLimit,
      BigDecimal totalSpent,
      BigDecimal totalRemaining,
      long onTrackCount,
      long warningCount,
      long exceededCount) {}

  @GetMapping
  public List<CategoryBudgetResponse> all() {
    Long userId = current.get().getId();
    List<Evaluation> monthly = monthlyEvaluations(userId, YearMonth.now());
    return categoryBudgets.findByUserIdOrderByCategoryAsc(userId).stream()
        .map(budget -> dto(budget, monthly))
        .toList();
  }

  @GetMapping("/overview")
  public CategoryBudgetOverview overview() {
    List<CategoryBudgetResponse> active =
        all().stream().filter(CategoryBudgetResponse::active).toList();
    BigDecimal limit = sum(active.stream().map(CategoryBudgetResponse::monthlyLimit).toList());
    BigDecimal spent = sum(active.stream().map(CategoryBudgetResponse::spent).toList());
    return new CategoryBudgetOverview(
        limit,
        spent,
        limit.subtract(spent),
        active.stream().filter(x -> "ON_TRACK".equals(x.status())).count(),
        active.stream().filter(x -> "WARNING".equals(x.status())).count(),
        active.stream().filter(x -> "EXCEEDED".equals(x.status())).count());
  }

  @PostMapping
  public CategoryBudgetResponse create(@Valid @RequestBody CategoryBudgetRequest request) {
    Long userId = current.get().getId();
    String category = normalize(request.category());
    if (categoryBudgets.findByUserIdAndCategoryIgnoreCase(userId, category).isPresent())
      throw new IllegalArgumentException("A budget already exists for this category");
    CategoryBudget budget = new CategoryBudget();
    budget.setUser(current.get());
    copy(budget, request, true);
    return dto(categoryBudgets.save(budget), monthlyEvaluations(userId, YearMonth.now()));
  }

  @PutMapping("/{id}")
  public CategoryBudgetResponse update(
      @PathVariable Long id, @Valid @RequestBody CategoryBudgetRequest request) {
    CategoryBudget budget = mine(id);
    String category = normalize(request.category());
    categoryBudgets
        .findByUserIdAndCategoryIgnoreCase(current.get().getId(), category)
        .filter(existing -> !existing.getId().equals(id))
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException("A budget already exists for this category");
            });
    copy(budget, request, false);
    return dto(
        categoryBudgets.save(budget), monthlyEvaluations(current.get().getId(), YearMonth.now()));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    categoryBudgets.delete(mine(id));
  }

  private void copy(CategoryBudget budget, CategoryBudgetRequest request, boolean creating) {
    budget.setCategory(normalize(request.category()));
    budget.setMonthlyLimit(request.monthlyLimit().setScale(2, RoundingMode.HALF_UP));
    budget.setWarningThreshold(
        request.warningThreshold() == null ? 80 : request.warningThreshold());
    budget.setActive(request.active() == null ? creating || budget.isActive() : request.active());
  }

  private CategoryBudgetResponse dto(CategoryBudget budget, List<Evaluation> monthly) {
    List<Evaluation> categoryItems =
        monthly.stream()
            .filter(
                e ->
                    e.getCategory() != null
                        && budget.getCategory().equalsIgnoreCase(e.getCategory().name()))
            .toList();
    BigDecimal spent = sumPrices(categoryItems, Enums.PurchaseDecision.PURCHASED);
    BigDecimal pending =
        categoryItems.stream()
            .filter(
                e ->
                    e.getDecision() == Enums.PurchaseDecision.PENDING
                        || e.getDecision() == Enums.PurchaseDecision.WAITING)
            .map(Evaluation::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal avoided = sumPrices(categoryItems, Enums.PurchaseDecision.AVOIDED);
    double percent =
        budget.getMonthlyLimit().signum() == 0
            ? 0
            : spent
                .multiply(BigDecimal.valueOf(100))
                .divide(budget.getMonthlyLimit(), 2, RoundingMode.HALF_UP)
                .doubleValue();
    String status =
        percent >= 100
            ? "EXCEEDED"
            : percent >= budget.getWarningThreshold() ? "WARNING" : "ON_TRACK";
    long purchases =
        categoryItems.stream()
            .filter(e -> e.getDecision() == Enums.PurchaseDecision.PURCHASED)
            .count();
    return new CategoryBudgetResponse(
        budget.getId(),
        budget.getCategory(),
        budget.getMonthlyLimit(),
        budget.getWarningThreshold(),
        budget.isActive(),
        spent,
        pending,
        avoided,
        budget.getMonthlyLimit().subtract(spent),
        percent,
        status,
        purchases,
        budget.getUpdatedAt());
  }

  private List<Evaluation> monthlyEvaluations(Long userId, YearMonth month) {
    return evaluations.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(e -> e.getCreatedAt() != null && YearMonth.from(e.getCreatedAt()).equals(month))
        .toList();
  }

  private BigDecimal sumPrices(List<Evaluation> items, Enums.PurchaseDecision decision) {
    return items.stream()
        .filter(e -> e.getDecision() == decision)
        .map(Evaluation::getPrice)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sum(List<BigDecimal> values) {
    return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }

  private CategoryBudget mine(Long id) {
    CategoryBudget budget =
        categoryBudgets
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Category budget not found"));
    if (!budget.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Category budget not found");
    return budget;
  }
}
