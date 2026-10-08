package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.dto.ApiDtos.*;
import com.mindfulcart.assistant.model.Evaluation;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {
  private final EvaluationRepository evaluations;
  private final OwnedItemRepository owned;
  private final CurrentUserService current;
  private final ImpulseScoreService scorer;

  @GetMapping
  public List<EvaluationResponse> all() {
    return evaluations.findByUserIdOrderByCreatedAtDesc(current.get().getId()).stream()
        .map(this::dto)
        .toList();
  }

  @PostMapping
  public EvaluationResponse create(@Valid @RequestBody EvaluationRequest r) {
    var user = current.get();
    var out = scorer.calculate(r);
    Evaluation e =
        Evaluation.builder()
            .user(user)
            .productName(r.productName())
            .category(r.category())
            .price(r.price())
            .description(r.description())
            .sourceOfInterest(r.sourceOfInterest())
            .discountAvailable(r.discountAvailable())
            .discountPercentage(r.discountPercentage())
            .genuinelyNeeded(r.genuinelyNeeded())
            .previouslyPlanned(r.previouslyPlanned())
            .considerationPeriod(r.considerationPeriod())
            .ownsSimilar(r.ownsSimilar())
            .withinBudget(r.withinBudget())
            .advertisementTriggered(r.advertisementTriggered())
            .discountTriggered(r.discountTriggered())
            .offerUrgency(r.offerUrgency())
            .emotionalState(r.emotionalState())
            .canWait(r.canWait())
            .frequentlyUsed(r.frequentlyUsed())
            .cheaperAlternative(r.cheaperAlternative())
            .impulseScore(out.score())
            .riskLevel(out.risk())
            .reasons(String.join("|", out.reasons()))
            .recommendation(out.recommendation())
            .waitingPeriod(out.waiting())
            .avoided(false)
            .build();
    return dto(evaluations.save(e));
  }

  @PutMapping("/{id}/decision")
  EvaluationResponse decision(@PathVariable Long id, @RequestBody Map<String, String> body) {
    Evaluation e = ownedEvaluation(id);
    var d =
        com.mindfulcart.assistant.model.Enums.PurchaseDecision.valueOf(
            body.getOrDefault("decision", "PENDING").toUpperCase());
    e.setDecision(d);
    e.setAvoided(d == com.mindfulcart.assistant.model.Enums.PurchaseDecision.AVOIDED);
    e.setDecidedAt(
        d == com.mindfulcart.assistant.model.Enums.PurchaseDecision.PENDING
            ? null
            : java.time.LocalDateTime.now());
    return dto(evaluations.save(e));
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable Long id) {
    Evaluation e = ownedEvaluation(id);
    evaluations.delete(e);
  }

  private Evaluation ownedEvaluation(Long id) {
    Evaluation e =
        evaluations
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Evaluation not found"));
    if (!e.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Evaluation not found");
    return e;
  }

  private EvaluationResponse dto(Evaluation e) {
    long count = owned.countByUserIdAndCategory(e.getUser().getId(), e.getCategory());
    String alert =
        count > 0
            ? "You already own "
                + count
                + " similar product"
                + (count == 1 ? "." : "s. Consider whether another one is necessary.")
            : null;
    var decision =
        e.getDecision() == null
            ? com.mindfulcart.assistant.model.Enums.PurchaseDecision.PENDING
            : e.getDecision();
    return new EvaluationResponse(
        e.getId(),
        e.getProductName(),
        e.getCategory(),
        e.getPrice(),
        e.getImpulseScore(),
        e.getRiskLevel(),
        e.getReasons() == null ? List.of() : Arrays.asList(e.getReasons().split("\\|")),
        e.getRecommendation(),
        e.getWaitingPeriod(),
        count,
        alert,
        decision,
        e.getDecidedAt(),
        e.getCreatedAt());
  }
}
