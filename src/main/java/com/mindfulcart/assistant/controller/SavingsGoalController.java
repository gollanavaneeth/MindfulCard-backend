package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.dto.ApiDtos.*;
import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import java.math.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class SavingsGoalController {
  private final SavingsGoalRepository goals;
  private final EvaluationRepository evaluations;
  private final CurrentUserService current;

  @GetMapping
  List<GoalResponse> all() {
    return goals.findByUserIdOrderByCreatedAtDesc(current.get().getId()).stream()
        .map(this::dto)
        .toList();
  }

  @PostMapping
  GoalResponse add(@Valid @RequestBody GoalRequest r) {
    var g = new SavingsGoal();
    g.setUser(current.get());
    copy(g, r);
    return dto(goals.save(g));
  }

  @PutMapping("/{id}")
  GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalRequest r) {
    var g = mine(id);
    copy(g, r);
    return dto(goals.save(g));
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable Long id) {
    goals.delete(mine(id));
  }

  private SavingsGoal mine(Long id) {
    var g = goals.findById(id).orElseThrow(() -> new IllegalArgumentException("Goal not found"));
    if (!g.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Goal not found");
    return g;
  }

  private void copy(SavingsGoal g, GoalRequest r) {
    g.setTitle(r.title());
    g.setTargetAmount(r.targetAmount());
    g.setManuallySaved(r.manuallySaved() == null ? BigDecimal.ZERO : r.manuallySaved());
    g.setTargetDate(r.targetDate());
  }

  private GoalResponse dto(SavingsGoal g) {
    BigDecimal avoided =
        evaluations.findByUserIdOrderByCreatedAtDesc(g.getUser().getId()).stream()
            .filter(x -> x.getDecision() == Enums.PurchaseDecision.AVOIDED)
            .map(Evaluation::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal total = g.getManuallySaved().add(avoided);
    double progress =
        g.getTargetAmount().signum() == 0
            ? 0
            : Math.min(
                100,
                total
                    .multiply(BigDecimal.valueOf(100))
                    .divide(g.getTargetAmount(), 2, RoundingMode.HALF_UP)
                    .doubleValue());
    return new GoalResponse(
        g.getId(),
        g.getTitle(),
        g.getTargetAmount(),
        g.getManuallySaved(),
        avoided,
        total,
        progress,
        g.getTargetDate());
  }
}
