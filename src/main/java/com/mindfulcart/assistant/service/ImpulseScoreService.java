package com.mindfulcart.assistant.service;

import com.mindfulcart.assistant.dto.ApiDtos.EvaluationRequest;
import com.mindfulcart.assistant.model.Enums.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ImpulseScoreService {
  public record Result(
      int score, RiskLevel risk, List<String> reasons, String recommendation, String waiting) {}

  public Result calculate(EvaluationRequest r) {
    int s = 0;
    List<String> x = new ArrayList<>();
    if (r.ownsSimilar()) {
      s += 20;
      x.add("You already own a similar product.");
    } else {
      s -= 10;
      x.add("You do not currently own a similar product.");
    }
    if (r.advertisementTriggered()) {
      s += 15;
      x.add("An advertisement influenced this purchase.");
    }
    if (r.discountTriggered()) {
      s += 10;
      x.add("The discount is influencing your decision.");
    }
    switch (r.emotionalState()) {
      case BORED -> {
        s += 15;
        x.add("Boredom may be driving the purchase.");
      }
      case STRESSED -> {
        s += 20;
        x.add("Stress may be driving the purchase.");
      }
      case SAD -> {
        s += 15;
        x.add("Your current sadness may affect the decision.");
      }
      case FRUSTRATED -> {
        s += 15;
        x.add("Frustration may affect the decision.");
      }
      case EXCITED -> {
        s += 8;
        x.add("Excitement may be increasing the urge to buy.");
      }
      default -> {}
    }
    if (!r.previouslyPlanned()) {
      s += 15;
      x.add("This purchase was not previously planned.");
    } else if (longConsideration(r.considerationPeriod())) {
      s -= 15;
      x.add("You have considered this purchase for a meaningful period.");
    }
    if (!r.withinBudget()) {
      s += 25;
      x.add("The product exceeds your available budget.");
    }
    if (r.offerUrgency()) {
      s += 10;
      x.add("A limited-time offer is creating urgency.");
    }
    if (r.genuinelyNeeded()) {
      s -= 20;
      x.add("You identified a genuine need for the product.");
    }
    if (r.frequentlyUsed()) {
      s -= 10;
      x.add("You expect to use the product frequently.");
    }
    if (r.cheaperAlternative()) {
      s += 5;
      x.add("A cheaper alternative is available.");
    }
    if (!r.canWait()) {
      s += 8;
      x.add("Difficulty waiting suggests a stronger impulse.");
    }
    s = Math.max(0, Math.min(100, s));
    RiskLevel risk =
        s <= 25
            ? RiskLevel.LOW
            : s <= 50 ? RiskLevel.MODERATE : s <= 75 ? RiskLevel.HIGH : RiskLevel.VERY_HIGH;
    String wait =
        switch (risk) {
          case LOW -> "No waiting period";
          case MODERATE -> "24 hours";
          case HIGH -> "48 hours";
          case VERY_HIGH -> "7 days";
        };
    String rec =
        switch (risk) {
          case LOW ->
              r.withinBudget() && r.genuinelyNeeded()
                  ? "This appears considered. Compare alternatives before proceeding."
                  : "Review your need and budget once more before buying.";
          case MODERATE -> "Pause and revisit the purchase after the cooling-off period.";
          case HIGH -> "Delay the purchase and consider adding it to your wishlist instead.";
          case VERY_HIGH ->
              "Reconsider this purchase. The responses show several strong impulse triggers.";
        };
    return new Result(s, risk, x, rec, wait);
  }

  private boolean longConsideration(String p) {
    if (p == null) return false;
    String v = p.toLowerCase();
    return v.contains("week") || v.contains("month") || v.contains("long");
  }
}
