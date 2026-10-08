package com.mindfulcart.assistant.service;

import com.mindfulcart.assistant.model.Budget;
import com.mindfulcart.assistant.model.Enums;
import com.mindfulcart.assistant.model.Evaluation;
import com.mindfulcart.assistant.repository.BudgetRepository;
import com.mindfulcart.assistant.repository.EvaluationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MonthlyInsightsService {
  private final EvaluationRepository evaluations;
  private final BudgetRepository budgets;

  public YearMonth parseMonth(String value) {
    if (value == null || value.isBlank()) return YearMonth.now();
    try {
      return YearMonth.parse(value);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Month must use YYYY-MM format");
    }
  }

  public List<Evaluation> evaluationsFor(Long userId, YearMonth month) {
    return evaluations.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(
            item ->
                item.getCreatedAt() != null && YearMonth.from(item.getCreatedAt()).equals(month))
        .toList();
  }

  public MonthlySummary summarize(Long userId, YearMonth month) {
    List<Evaluation> items = evaluationsFor(userId, month);
    int evaluated = items.size();
    long purchased = countDecision(items, Enums.PurchaseDecision.PURCHASED);
    long avoided = countDecision(items, Enums.PurchaseDecision.AVOIDED);
    long waiting = countDecision(items, Enums.PurchaseDecision.WAITING);
    long pending = countDecision(items, Enums.PurchaseDecision.PENDING);
    BigDecimal purchasedAmount = sumFor(items, Enums.PurchaseDecision.PURCHASED);
    BigDecimal avoidedAmount = sumFor(items, Enums.PurchaseDecision.AVOIDED);
    double averageScore =
        round(items.stream().mapToInt(Evaluation::getImpulseScore).average().orElse(0));
    long highRisk =
        items.stream()
            .filter(
                item ->
                    item.getRiskLevel() == Enums.RiskLevel.HIGH
                        || item.getRiskLevel() == Enums.RiskLevel.VERY_HIGH)
            .count();

    Map<String, Long> categories = frequency(items, item -> safeName(item.getCategory(), "OTHER"));
    Map<String, Long> decisions = frequency(items, item -> decision(item).name());
    Map<String, Long> emotions =
        frequency(items, item -> safeName(item.getEmotionalState(), "NORMAL"));
    Map<String, Long> risks = frequency(items, item -> safeName(item.getRiskLevel(), "UNKNOWN"));
    Map<String, Long> triggers = triggers(items);

    String topCategory = top(categories, "No category yet");
    String topTrigger = top(triggers, "No recurring trigger yet");
    String topEmotion = topExcluding(emotions, "NORMAL", "Mostly neutral");
    List<DailyTrend> dailyTrend =
        items.stream()
            .collect(Collectors.groupingBy(item -> item.getCreatedAt().toLocalDate()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                entry ->
                    new DailyTrend(
                        entry.getKey(),
                        entry.getValue().size(),
                        round(
                            entry.getValue().stream()
                                .mapToInt(Evaluation::getImpulseScore)
                                .average()
                                .orElse(0)),
                        entry.getValue().stream()
                            .filter(item -> decision(item) == Enums.PurchaseDecision.AVOIDED)
                            .count()))
            .toList();

    BudgetSnapshot budget = budgetSnapshot(userId, purchasedAmount);
    List<String> insights =
        insights(
            evaluated,
            avoided,
            purchased,
            highRisk,
            averageScore,
            topTrigger,
            topCategory,
            topEmotion,
            avoidedAmount);
    String narrative =
        narrative(month, evaluated, avoided, purchased, averageScore, avoidedAmount, topTrigger);
    double avoidanceRate = evaluated == 0 ? 0 : round(avoided * 100.0 / evaluated);
    double purchaseRate = evaluated == 0 ? 0 : round(purchased * 100.0 / evaluated);

    return new MonthlySummary(
        month.toString(),
        month.atDay(1),
        month.atEndOfMonth(),
        evaluated,
        purchased,
        avoided,
        waiting,
        pending,
        purchasedAmount,
        avoidedAmount,
        averageScore,
        highRisk,
        avoidanceRate,
        purchaseRate,
        topCategory,
        topTrigger,
        topEmotion,
        categories,
        decisions,
        triggers,
        emotions,
        risks,
        dailyTrend,
        budget,
        narrative,
        insights);
  }

  private long countDecision(List<Evaluation> items, Enums.PurchaseDecision value) {
    return items.stream().filter(item -> decision(item) == value).count();
  }

  private BigDecimal sumFor(List<Evaluation> items, Enums.PurchaseDecision value) {
    return items.stream()
        .filter(item -> decision(item) == value)
        .map(Evaluation::getPrice)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Enums.PurchaseDecision decision(Evaluation item) {
    return item.getDecision() == null ? Enums.PurchaseDecision.PENDING : item.getDecision();
  }

  private Map<String, Long> frequency(
      List<Evaluation> items, Function<Evaluation, String> classifier) {
    Map<String, Long> unsorted =
        items.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    return sorted(unsorted);
  }

  private Map<String, Long> triggers(List<Evaluation> items) {
    Map<String, Long> values = new LinkedHashMap<>();
    for (Evaluation item : items) {
      if (item.isAdvertisementTriggered()) values.merge("Advertisement", 1L, Long::sum);
      if (item.isDiscountTriggered() || item.isDiscountAvailable())
        values.merge("Discount", 1L, Long::sum);
      if (item.isOfferUrgency()) values.merge("Urgency", 1L, Long::sum);
      if (!item.isPreviouslyPlanned()) values.merge("Unplanned", 1L, Long::sum);
      if (item.isOwnsSimilar()) values.merge("Already owned similar", 1L, Long::sum);
      if (item.getEmotionalState() != null
          && item.getEmotionalState() != Enums.EmotionalState.NORMAL) {
        values.merge("Emotion: " + item.getEmotionalState().name(), 1L, Long::sum);
      }
    }
    return sorted(values);
  }

  private Map<String, Long> sorted(Map<String, Long> values) {
    return values.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private String top(Map<String, Long> values, String fallback) {
    return values.keySet().stream().findFirst().orElse(fallback);
  }

  private String topExcluding(Map<String, Long> values, String excluded, String fallback) {
    return values.keySet().stream()
        .filter(key -> !key.equals(excluded))
        .findFirst()
        .orElse(fallback);
  }

  private BudgetSnapshot budgetSnapshot(Long userId, BigDecimal spent) {
    Budget value = budgets.findByUserId(userId).orElse(null);
    if (value == null) return new BudgetSnapshot(false, BigDecimal.ZERO, spent, BigDecimal.ZERO, 0);
    BigDecimal available = value.getDiscretionaryBudget();
    BigDecimal remaining = available.subtract(spent);
    double utilization =
        available.signum() <= 0
            ? 0
            : spent
                .multiply(BigDecimal.valueOf(100))
                .divide(available, 1, RoundingMode.HALF_UP)
                .doubleValue();
    return new BudgetSnapshot(true, available, spent, remaining, utilization);
  }

  private List<String> insights(
      int evaluated,
      long avoided,
      long purchased,
      long highRisk,
      double average,
      String trigger,
      String category,
      String emotion,
      BigDecimal saved) {
    List<String> result = new ArrayList<>();
    if (evaluated == 0) {
      result.add("Evaluate purchases this month to unlock personalized patterns.");
      result.add("Start with purchases that were discovered through ads or limited-time offers.");
      return result;
    }
    if (avoided > 0)
      result.add(
          "You paused or avoided " + avoided + " purchase(s), protecting " + money(saved) + ".");
    if (purchased > avoided)
      result.add(
          "More evaluated items were purchased than avoided; try extending the cooling-off period for non-essential categories.");
    if (highRisk > 0)
      result.add(
          highRisk
              + " high-risk decision(s) appeared this month. Revisit their common triggers before buying.");
    if (average >= 60)
      result.add(
          "Your average impulse score is elevated at "
              + average
              + "; use a 48-hour minimum wait for discretionary purchases.");
    else
      result.add(
          "Your average impulse score is "
              + average
              + ", showing generally considered decision-making.");
    if (!trigger.startsWith("No "))
      result.add(
          "Your most frequent trigger was "
              + trigger
              + ". Create a personal rule specifically for it.");
    if (!category.startsWith("No "))
      result.add(category + " was your most evaluated category this month.");
    if (!emotion.equals("Mostly neutral"))
      result.add("The emotional state most associated with evaluations was " + emotion + ".");
    return result.stream().distinct().limit(6).toList();
  }

  private String narrative(
      YearMonth month,
      int evaluated,
      long avoided,
      long purchased,
      double average,
      BigDecimal saved,
      String trigger) {
    if (evaluated == 0)
      return "No purchase decisions were evaluated in "
          + month
          + ". Add an evaluation to begin your monthly reflection.";
    return "In "
        + month
        + ", you evaluated "
        + evaluated
        + " purchase(s), avoided "
        + avoided
        + ", purchased "
        + purchased
        + ", and protected "
        + money(saved)
        + ". Your average impulse score was "
        + average
        + ". The leading trigger was "
        + trigger
        + ".";
  }

  private String money(BigDecimal value) {
    return "₹" + value.setScale(0, RoundingMode.HALF_UP).toPlainString();
  }

  private String safeName(Enum<?> value, String fallback) {
    return value == null ? fallback : value.name();
  }

  private double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  public record DailyTrend(LocalDate date, int evaluated, double averageScore, long avoided) {}

  public record BudgetSnapshot(
      boolean configured,
      BigDecimal discretionaryBudget,
      BigDecimal purchasedAmount,
      BigDecimal remaining,
      double utilizationPercentage) {}

  public record MonthlySummary(
      String month,
      LocalDate periodStart,
      LocalDate periodEnd,
      int evaluated,
      long purchased,
      long avoided,
      long waiting,
      long pending,
      BigDecimal purchasedAmount,
      BigDecimal avoidedAmount,
      double averageImpulseScore,
      long highRiskCount,
      double avoidanceRate,
      double purchaseRate,
      String topCategory,
      String topTrigger,
      String topEmotion,
      Map<String, Long> categories,
      Map<String, Long> decisions,
      Map<String, Long> triggers,
      Map<String, Long> emotions,
      Map<String, Long> risks,
      List<DailyTrend> dailyTrend,
      BudgetSnapshot budget,
      String narrative,
      List<String> insights) {}
}
