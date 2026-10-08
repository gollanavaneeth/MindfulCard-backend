package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.*;
import com.mindfulcart.assistant.service.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coach")
@RequiredArgsConstructor
public class CoachController {
  private final EvaluationRepository evaluationRepository;
  private final WishlistRepository wishlistRepository;
  private final CurrentUserService currentUserService;
  private final AiAdvisorService aiAdvisorService;

  public record Ask(String question) {}

  @PostMapping("/ask")
  AiAdvisorService.Answer ask(@RequestBody Ask request) {
    if (request.question() == null || request.question().isBlank()) {
      throw new IllegalArgumentException("Ask a question first");
    }

    List<Evaluation> userHistory =
        evaluationRepository.findByUserIdOrderByCreatedAtDesc(currentUserService.get().getId());
    return aiAdvisorService.answer(request.question().trim(), userHistory);
  }

  @GetMapping
  Map<String, Object> insight() {
    var all =
        evaluationRepository.findByUserIdOrderByCreatedAtDesc(currentUserService.get().getId());
    var waiting =
        wishlistRepository
            .findByUserIdOrderByAddedDateDesc(currentUserService.get().getId())
            .stream()
            .filter(x -> x.getStatus() == Enums.WishlistStatus.WAITING)
            .toList();
    long avoided =
        all.stream().filter(x -> x.getDecision() == Enums.PurchaseDecision.AVOIDED).count();
    int streak = streak(all);
    String headline, body;
    if (all.isEmpty()) {
      headline = "Build your impulse profile";
      body =
          "Complete your first purchase check. I’ll learn which situations make spending harder for you.";
    } else {
      var top =
          all.stream()
              .collect(
                  java.util.stream.Collectors.groupingBy(
                      Evaluation::getEmotionalState, java.util.stream.Collectors.counting()))
              .entrySet()
              .stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse(Enums.EmotionalState.NORMAL);
      headline =
          avoided > 0
              ? "Your pause is becoming a habit"
              : "Your strongest pattern is becoming visible";
      body =
          "Your most common shopping mood is "
              + top.name().toLowerCase()
              + ". "
              + (waiting.isEmpty()
                  ? "Move uncertain purchases to the cooling-off list before deciding."
                  : "You currently have "
                      + waiting.size()
                      + " item(s) cooling off—revisit them only after the timer ends.");
    }
    List<Map<String, Object>> badges = new ArrayList<>();
    badges.add(badge("First pause", "Complete your first evaluation", all.size() >= 1));
    badges.add(badge("Mindful five", "Evaluate five purchases", all.size() >= 5));
    badges.add(badge("Impulse breaker", "Avoid three purchases", avoided >= 3));
    badges.add(badge("Week of intention", "Evaluate on three different days", streak >= 3));
    return Map.of(
        "headline",
        headline,
        "message",
        body,
        "streak",
        streak,
        "avoidedCount",
        avoided,
        "waitingCount",
        waiting.size(),
        "badges",
        badges,
        "weeklyChallenge",
        Map.of(
            "title",
            "The 24-hour pause",
            "description",
            "Put one unplanned item on your wishlist and wait a full day.",
            "progress",
            Math.min(1, waiting.size()),
            "target",
            1));
  }

  private Map<String, Object> badge(String n, String d, boolean unlocked) {
    return Map.of("name", n, "description", d, "unlocked", unlocked);
  }

  private int streak(List<Evaluation> a) {
    return (int) a.stream().map(x -> x.getCreatedAt().toLocalDate()).distinct().count();
  }
}
