package com.mindfulcart.assistant.service;

import com.mindfulcart.assistant.model.Enums;
import com.mindfulcart.assistant.model.Evaluation;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AiAdvisorService {
  public record Answer(String answer, String source) {}

  public Answer answer(String question, List<Evaluation> history) {
    List<Evaluation> relevantHistory = retrieveRelevantHistory(question, history);
    return new Answer(createLocalAnswer(question, relevantHistory), "LOCAL_RULE_ENGINE");
  }

  // Local retrieval: prioritize recent records that share words with the question.
  private List<Evaluation> retrieveRelevantHistory(
      String question, List<Evaluation> completeHistory) {
    List<Evaluation> recentHistory = completeHistory.stream().limit(8).toList();
    List<String> questionWords =
        Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .filter(word -> word.length() >= 4)
            .toList();
    List<Evaluation> matches =
        recentHistory.stream()
            .filter(
                evaluation -> {
                  String searchableText =
                      (evaluation.getProductName() + " " + evaluation.getCategory())
                          .toLowerCase(Locale.ROOT);
                  return questionWords.stream().anyMatch(searchableText::contains);
                })
            .toList();
    return matches.isEmpty() ? recentHistory : matches;
  }

  // Local generation: deterministic templates use only retrieved user data.
  private String createLocalAnswer(String question, List<Evaluation> history) {
    if (history.isEmpty()) {
      return "Start with one purchase evaluation. Describe what you want, whether it was "
          + "planned, and how you feel. A small pause creates enough distance to make a "
          + "clearer choice.";
    }

    String normalizedQuestion = question.toLowerCase(Locale.ROOT);
    double averageScore =
        history.stream().mapToInt(Evaluation::getImpulseScore).average().orElse(0);
    long advertisementTriggers =
        history.stream().filter(Evaluation::isAdvertisementTriggered).count();
    long urgencyTriggers = history.stream().filter(Evaluation::isOfferUrgency).count();
    long avoidedPurchases =
        history.stream()
            .filter(evaluation -> evaluation.getDecision() == Enums.PurchaseDecision.AVOIDED)
            .count();
    String mainTrigger =
        advertisementTriggers >= urgencyTriggers && advertisementTriggers > 0
            ? "advertising"
            : urgencyTriggers > 0 ? "limited-time urgency" : "unplanned browsing";

    if (normalizedQuestion.matches(".*(buy|purchase|worth|should).*")) {
      return "Before deciding, try the replacement test: if this item disappeared today, what "
          + "existing item or cheaper option would solve the same need? Your relevant purchase "
          + "history has an average impulse score of "
          + Math.round(averageScore)
          + ", and the strongest visible trigger is "
          + mainTrigger
          + ". Give the decision at least 24 hours, then reassess the need—not the deal.";
    }

    if (normalizedQuestion.contains("trigger") || normalizedQuestion.contains("pattern")) {
      return "Your strongest visible pattern is "
          + mainTrigger
          + ". Across the retrieved records, you avoided "
          + avoidedPurchases
          + " purchase(s). Reduce exposure to that trigger and move uncertain items to the "
          + "cooling-off list before deciding.";
    }

    return "Your recent reflections suggest that "
        + mainTrigger
        + " deserves the most attention. The relevant records have an average impulse score of "
        + Math.round(averageScore)
        + ". Turn off promotional notifications, keep uncertain items in the cooling-off list, "
        + "and revisit them only after the timer ends.";
  }
}
