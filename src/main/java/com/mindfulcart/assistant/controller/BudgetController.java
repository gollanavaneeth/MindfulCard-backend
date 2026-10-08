package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.dto.ApiDtos.*;
import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {
  private final BudgetRepository budgets;
  private final EvaluationRepository evaluations;
  private final CurrentUserService current;

  @GetMapping
  BudgetResponse get() {
    return budgets.findByUserId(current.get().getId()).map(this::dto).orElse(empty());
  }

  @PostMapping
  BudgetResponse create(@Valid @RequestBody BudgetRequest r) {
    return upsert(r);
  }

  @PutMapping
  BudgetResponse update(@Valid @RequestBody BudgetRequest r) {
    return upsert(r);
  }

  private BudgetResponse upsert(BudgetRequest r) {
    Budget b = budgets.findByUserId(current.get().getId()).orElseGet(Budget::new);
    b.setUser(current.get());
    b.setMonthlyIncome(r.monthlyIncome());
    b.setEssentialExpenses(r.essentialExpenses());
    b.setSavingsTarget(r.savingsTarget());
    return dto(budgets.save(b));
  }

  private BudgetResponse empty() {
    return new BudgetResponse(
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null);
  }

  private BudgetResponse dto(Budget b) {
    LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
    List<Evaluation> month =
        evaluations.findByUserIdOrderByCreatedAtDesc(b.getUser().getId()).stream()
            .filter(x -> !x.getCreatedAt().isBefore(start))
            .toList();
    BigDecimal evaluated = sum(month);
    BigDecimal purchased =
        sum(
            month.stream()
                .filter(x -> x.getDecision() == Enums.PurchaseDecision.PURCHASED)
                .toList());
    BigDecimal exposure =
        sum(
            month.stream()
                .filter(
                    x ->
                        x.getDecision() == null
                            || x.getDecision() == Enums.PurchaseDecision.PENDING
                            || x.getDecision() == Enums.PurchaseDecision.WAITING)
                .toList());
    BigDecimal remaining = b.getDiscretionaryBudget().subtract(purchased);
    String alert =
        remaining.signum() < 0
            ? "Confirmed purchases exceed your monthly discretionary budget."
            : b.getDiscretionaryBudget().signum() > 0
                    && remaining.compareTo(
                            b.getDiscretionaryBudget().multiply(new BigDecimal("0.20")))
                        <= 0
                ? "You are nearing your monthly spending limit."
                : exposure.compareTo(remaining) > 0
                    ? "Pending decisions could exceed your remaining budget."
                    : null;
    return new BudgetResponse(
        b.getId(),
        b.getMonthlyIncome(),
        b.getEssentialExpenses(),
        b.getSavingsTarget(),
        b.getDiscretionaryBudget(),
        evaluated,
        purchased,
        exposure,
        remaining,
        alert);
  }

  private BigDecimal sum(List<Evaluation> x) {
    return x.stream().map(Evaluation::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
