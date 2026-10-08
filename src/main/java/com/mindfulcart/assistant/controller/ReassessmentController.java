package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reassessments")
@RequiredArgsConstructor
public class ReassessmentController {
  private final ReassessmentRepository repo;
  private final EvaluationRepository evaluations;
  private final CurrentUserService current;

  public record Request(
      @NotNull Long evaluationId,
      boolean stillWantIt,
      boolean needChanged,
      boolean priceChanged,
      boolean foundAlternative,
      boolean fitsBudgetNow,
      String reflection) {}

  public record Response(
      Long id,
      Long evaluationId,
      String productName,
      int originalScore,
      int updatedScore,
      int change,
      boolean stillWantIt,
      String reflection,
      LocalDateTime createdAt) {}

  @GetMapping
  public List<Response> all() {
    return repo.findByUserIdOrderByCreatedAtDesc(current.get().getId()).stream()
        .map(this::dto)
        .toList();
  }

  @GetMapping("/evaluation/{id}")
  List<Response> forEvaluation(@PathVariable Long id) {
    return repo.findByEvaluationIdAndUserIdOrderByCreatedAtDesc(id, current.get().getId()).stream()
        .map(this::dto)
        .toList();
  }

  @PostMapping
  Response create(@RequestBody Request r) {
    var user = current.get();
    var e =
        evaluations
            .findById(r.evaluationId())
            .filter(x -> x.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Evaluation not found"));
    int score = e.getImpulseScore();
    if (!r.stillWantIt()) score -= 25;
    if (r.needChanged()) score -= 15;
    if (r.foundAlternative()) score -= 15;
    if (r.fitsBudgetNow()) score -= 8;
    else score += 10;
    if (r.priceChanged()) score += 5;
    score = Math.max(0, Math.min(100, score));
    var saved =
        repo.save(
            Reassessment.builder()
                .user(user)
                .evaluation(e)
                .stillWantIt(r.stillWantIt())
                .needChanged(r.needChanged())
                .priceChanged(r.priceChanged())
                .foundAlternative(r.foundAlternative())
                .fitsBudgetNow(r.fitsBudgetNow())
                .updatedScore(score)
                .reflection(r.reflection())
                .build());
    return dto(saved);
  }

  private Response dto(Reassessment r) {
    return new Response(
        r.getId(),
        r.getEvaluation().getId(),
        r.getEvaluation().getProductName(),
        r.getEvaluation().getImpulseScore(),
        r.getUpdatedScore(),
        r.getUpdatedScore() - r.getEvaluation().getImpulseScore(),
        r.isStillWantIt(),
        r.getReflection(),
        r.getCreatedAt());
  }
}
