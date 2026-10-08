package com.mindfulcart.assistant.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ComparisonControllerTest {
  private final ComparisonController controller = new ComparisonController();

  @Test
  void comparesTrueOwnershipCostAndSelectsStrongerValue() {
    var durableLaptop =
        option("Durable laptop", 60_000, 10, 0, 2_000, 10_000, 600, 3, 24, 14, 5, 5, 4.5, false);
    var cheaperLaptop =
        option("Cheaper laptop", 45_000, 0, 500, 5_000, 5_000, 50, 2, 6, 7, 3, 3, 3, true);

    var response =
        controller.compare(
            new ComparisonController.Request(
                durableLaptop, cheaperLaptop, BigDecimal.valueOf(70_000)));

    assertEquals("Durable laptop", response.winner());
    assertEquals(new BigDecimal("50000.00"), response.first().totalOwnershipCost());
    assertTrue(response.first().valueScore() > response.second().valueScore());
    assertEquals(6, response.first().breakdown().size());
  }

  @Test
  void reportsTieForIdenticalOptions() {
    var option = option("Same value", 10_000, 5, 0, 0, 1_000, 100, 2, 12, 7, 3, 3, 3, false);

    var response =
        controller.compare(
            new ComparisonController.Request(option, option, BigDecimal.valueOf(20_000)));

    assertEquals("Tie", response.winner());
    assertEquals("NEAR_IDENTICAL", response.verdict());
    assertEquals(0, response.scoreGap());
  }

  private ComparisonController.Option option(
      String name,
      double listPrice,
      double discount,
      double shipping,
      double maintenance,
      double resale,
      int uses,
      int lifespan,
      int warranty,
      int returns,
      int need,
      int quality,
      double userRating,
      boolean ownsAlternative) {
    return new ComparisonController.Option(
        name,
        "ELECTRONICS",
        BigDecimal.valueOf(listPrice),
        discount,
        BigDecimal.valueOf(shipping),
        BigDecimal.valueOf(maintenance),
        BigDecimal.valueOf(resale),
        uses,
        lifespan,
        warranty,
        returns,
        need,
        quality,
        userRating,
        ownsAlternative);
  }
}
