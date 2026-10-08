package com.mindfulcart.assistant.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comparisons")
public class ComparisonController {
  public record Option(
      @NotBlank String name,
      @NotBlank String category,
      @NotNull @Positive BigDecimal listPrice,
      @DecimalMin("0") @DecimalMax("100") double discountPercentage,
      @NotNull @PositiveOrZero BigDecimal shippingCost,
      @NotNull @PositiveOrZero BigDecimal annualMaintenanceCost,
      @NotNull @PositiveOrZero BigDecimal expectedResaleValue,
      @Positive int expectedUses,
      @Min(1) @Max(20) int lifespanYears,
      @PositiveOrZero @Max(240) int warrantyMonths,
      @PositiveOrZero @Max(365) int returnWindowDays,
      @Min(1) @Max(5) int needRating,
      @Min(1) @Max(5) int qualityRating,
      @DecimalMin("1") @DecimalMax("5") double userRating,
      boolean ownsAlternative) {}

  public record Request(
      @Valid @NotNull Option first,
      @Valid @NotNull Option second,
      @PositiveOrZero BigDecimal availableBudget) {}

  public record Result(
      String name,
      String category,
      BigDecimal listPrice,
      BigDecimal finalPrice,
      BigDecimal priceSavings,
      BigDecimal totalOwnershipCost,
      BigDecimal costPerUse,
      double budgetImpactPercent,
      double valueScore,
      Map<String, Double> breakdown,
      List<String> strengths,
      List<String> watchouts) {}

  public record Response(
      Result first,
      Result second,
      String winner,
      String verdict,
      double scoreGap,
      BigDecimal upfrontDifference,
      BigDecimal ownershipDifference,
      String explanation) {}

  @PostMapping
  Response compare(@Valid @RequestBody Request request) {
    Result first = score(request.first(), request.availableBudget());
    Result second = score(request.second(), request.availableBudget());
    double scoreGap = round(Math.abs(first.valueScore() - second.valueScore()));

    String winner;
    String verdict;
    if (scoreGap < 1) {
      winner = "Tie";
      verdict = "NEAR_IDENTICAL";
    } else if (first.valueScore() > second.valueScore()) {
      winner = first.name();
      verdict = scoreGap < 7 ? "CLOSE_CALL" : "CLEAR_WINNER";
    } else {
      winner = second.name();
      verdict = scoreGap < 7 ? "CLOSE_CALL" : "CLEAR_WINNER";
    }

    String explanation = createExplanation(first, second, winner, verdict);
    return new Response(
        first,
        second,
        winner,
        verdict,
        scoreGap,
        first.finalPrice().subtract(second.finalPrice()).abs(),
        first.totalOwnershipCost().subtract(second.totalOwnershipCost()).abs(),
        explanation);
  }

  private Result score(Option option, BigDecimal availableBudget) {
    BigDecimal discountRate = BigDecimal.valueOf(option.discountPercentage()).movePointLeft(2);
    BigDecimal finalPrice =
        option
            .listPrice()
            .multiply(BigDecimal.ONE.subtract(discountRate))
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal maintenanceCost =
        option.annualMaintenanceCost().multiply(BigDecimal.valueOf(option.lifespanYears()));
    BigDecimal totalOwnershipCost =
        finalPrice
            .add(option.shippingCost())
            .add(maintenanceCost)
            .subtract(option.expectedResaleValue())
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal costPerUse =
        totalOwnershipCost.divide(
            BigDecimal.valueOf(option.expectedUses()), 2, RoundingMode.HALF_UP);
    BigDecimal priceSavings =
        option.listPrice().subtract(finalPrice).setScale(2, RoundingMode.HALF_UP);

    Map<String, Double> breakdown = createBreakdown(option, finalPrice, maintenanceCost);
    double valueScore = round(breakdown.values().stream().mapToDouble(Double::doubleValue).sum());
    double budgetImpact =
        availableBudget == null || availableBudget.signum() == 0
            ? 0
            : round(
                finalPrice.divide(availableBudget, 4, RoundingMode.HALF_UP).doubleValue() * 100);

    List<String> strengths = strengths(option, costPerUse);
    List<String> watchouts = watchouts(option, finalPrice, maintenanceCost, budgetImpact);
    return new Result(
        option.name(),
        option.category(),
        option.listPrice(),
        finalPrice,
        priceSavings,
        totalOwnershipCost,
        costPerUse,
        budgetImpact,
        valueScore,
        breakdown,
        strengths,
        watchouts);
  }

  private Map<String, Double> createBreakdown(
      Option option, BigDecimal finalPrice, BigDecimal maintenanceCost) {
    Map<String, Double> scores = new LinkedHashMap<>();
    double need = option.needRating() * 5.0 - (option.ownsAlternative() ? 6 : 0);
    double quality = ((option.qualityRating() + option.userRating()) / 10.0) * 20;
    double usage = Math.min(20, Math.log1p(option.expectedUses()) / Math.log1p(500) * 20);
    double resaleRatio =
        option
            .expectedResaleValue()
            .divide(finalPrice.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
            .doubleValue();
    double maintenanceRatio =
        maintenanceCost
            .divide(finalPrice.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
            .doubleValue();
    double shippingRatio =
        option
            .shippingCost()
            .divide(finalPrice.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
            .doubleValue();
    double ownership =
        8
            + Math.min(3, option.discountPercentage() * 0.03)
            + Math.min(4, resaleRatio * 10)
            - Math.min(5, maintenanceRatio * 10)
            - Math.min(3, shippingRatio * 10);
    double protection =
        Math.min(6, option.warrantyMonths() / 4.0) + Math.min(4, option.returnWindowDays() / 7.5);
    double longevity = Math.min(10, option.lifespanYears() * 2.0);

    scores.put("Need fit", round(clamp(need, 0, 25)));
    scores.put("Quality confidence", round(clamp(quality, 0, 20)));
    scores.put("Usage potential", round(clamp(usage, 0, 20)));
    scores.put("Ownership value", round(clamp(ownership, 0, 15)));
    scores.put("Buyer protection", round(clamp(protection, 0, 10)));
    scores.put("Longevity", round(clamp(longevity, 0, 10)));
    return scores;
  }

  private List<String> strengths(Option option, BigDecimal costPerUse) {
    List<String> strengths = new ArrayList<>();
    if (option.needRating() >= 4) strengths.add("Strong need fit");
    if (option.expectedUses() >= 100) strengths.add("High expected lifetime usage");
    if (option.warrantyMonths() >= 12) strengths.add("Useful warranty coverage");
    if (option.returnWindowDays() >= 14) strengths.add("Flexible return window");
    if (option.discountPercentage() >= 20) strengths.add("Meaningful upfront discount");
    if (costPerUse.compareTo(BigDecimal.valueOf(100)) <= 0)
      strengths.add("Low estimated cost per use");
    if (strengths.isEmpty())
      strengths.add("No dominant strength; compare the trade-offs carefully");
    return strengths;
  }

  private List<String> watchouts(
      Option option, BigDecimal finalPrice, BigDecimal maintenanceCost, double budgetImpact) {
    List<String> watchouts = new ArrayList<>();
    if (option.ownsAlternative()) watchouts.add("You already own a usable alternative");
    if (option.needRating() <= 2) watchouts.add("Low need rating increases impulse risk");
    if (option.expectedUses() < 25) watchouts.add("Low expected usage");
    if (option.warrantyMonths() == 0) watchouts.add("No warranty protection");
    if (option.returnWindowDays() == 0) watchouts.add("No return window");
    if (maintenanceCost.compareTo(finalPrice.multiply(BigDecimal.valueOf(0.25))) > 0)
      watchouts.add("Maintenance is high relative to purchase price");
    if (budgetImpact > 100) watchouts.add("Price exceeds the available monthly budget");
    if (option.userRating() < 3) watchouts.add("Weak public rating confidence");
    if (watchouts.isEmpty()) watchouts.add("No major warning from the supplied details");
    return watchouts;
  }

  private String createExplanation(Result first, Result second, String winner, String verdict) {
    if ("Tie".equals(winner)) {
      return "The options are nearly identical on overall value. Prefer the lower ownership cost or wait until one option clearly fits your need better.";
    }
    Result best = first.name().equals(winner) ? first : second;
    Result other = best == first ? second : first;
    String closeness =
        "CLOSE_CALL".equals(verdict)
            ? "This is a close decision. "
            : "The evidence shows a meaningful value difference. ";
    return closeness
        + best.name()
        + " scores higher after considering need, quality, expected use, ownership cost, protection, and longevity. "
        + (best.totalOwnershipCost().compareTo(other.totalOwnershipCost()) <= 0
            ? "It also has the lower estimated ownership cost."
            : "It costs more to own, so confirm that its non-price advantages matter to you.");
  }

  private double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private double round(double value) {
    return Math.round(value * 10) / 10.0;
  }
}
