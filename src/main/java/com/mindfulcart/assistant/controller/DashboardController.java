package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.Enums.PurchaseDecision;
import com.mindfulcart.assistant.model.Evaluation;
import com.mindfulcart.assistant.repository.EvaluationRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
  private final EvaluationRepository repo;
  private final CurrentUserService current;

  @GetMapping
  public Map<String, Object> get() {
    var all = repo.findByUserIdOrderByCreatedAtDesc(current.get().getId());
    long avoided = all.stream().filter(x -> x.getDecision() == PurchaseDecision.AVOIDED).count();
    BigDecimal saved =
        all.stream()
            .filter(x -> x.getDecision() == PurchaseDecision.AVOIDED)
            .map(Evaluation::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    double avg = all.stream().mapToInt(Evaluation::getImpulseScore).average().orElse(0);
    Map<String, Long> categories = new LinkedHashMap<>(),
        decisions = new LinkedHashMap<>(),
        triggers = new LinkedHashMap<>();
    all.forEach(
        x -> {
          categories.merge(x.getCategory().name(), 1L, Long::sum);
          decisions.merge(
              (x.getDecision() == null ? PurchaseDecision.PENDING : x.getDecision()).name(),
              1L,
              Long::sum);
          if (x.isAdvertisementTriggered()) triggers.merge("Advertisement", 1L, Long::sum);
          if (x.isDiscountTriggered()) triggers.merge("Discount", 1L, Long::sum);
          if (x.isOfferUrgency()) triggers.merge("Urgency", 1L, Long::sum);
          if (!x.isPreviouslyPlanned()) triggers.merge("Unplanned", 1L, Long::sum);
          if (x.getEmotionalState().ordinal() >= 3)
            triggers.merge(x.getEmotionalState().name(), 1L, Long::sum);
        });
    String top =
        triggers.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("No pattern yet");
    String cat =
        categories.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("No category yet");
    long month =
        all.stream()
            .filter(
                x -> !x.getCreatedAt().toLocalDate().isBefore(LocalDate.now().withDayOfMonth(1)))
            .count();
    return Map.ofEntries(
        Map.entry("evaluated", all.size()),
        Map.entry("thisMonth", month),
        Map.entry("avoided", avoided),
        Map.entry("moneySaved", saved),
        Map.entry("averageScore", Math.round(avg * 10) / 10.0),
        Map.entry("commonTrigger", top),
        Map.entry("topCategory", cat),
        Map.entry("categories", categories),
        Map.entry("decisions", decisions),
        Map.entry("triggers", triggers),
        Map.entry(
            "recent",
            all.stream()
                .limit(6)
                .map(
                    x ->
                        Map.of(
                            "id",
                            x.getId(),
                            "productName",
                            x.getProductName(),
                            "category",
                            x.getCategory(),
                            "price",
                            x.getPrice(),
                            "score",
                            x.getImpulseScore(),
                            "riskLevel",
                            x.getRiskLevel(),
                            "decision",
                            x.getDecision() == null ? PurchaseDecision.PENDING : x.getDecision(),
                            "createdAt",
                            x.getCreatedAt()))
                .toList()),
        Map.entry(
            "scoreTrend",
            all.stream()
                .limit(12)
                .sorted(Comparator.comparing(Evaluation::getCreatedAt))
                .map(
                    x ->
                        Map.of(
                            "label",
                            x.getCreatedAt().toLocalDate().toString(),
                            "value",
                            x.getImpulseScore()))
                .toList()));
  }
}
