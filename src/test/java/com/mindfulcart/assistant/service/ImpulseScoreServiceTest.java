package com.mindfulcart.assistant.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mindfulcart.assistant.dto.ApiDtos.EvaluationRequest;
import com.mindfulcart.assistant.model.Enums.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ImpulseScoreServiceTest {
  private final ImpulseScoreService service = new ImpulseScoreService();

  @Test
  void identifiesVeryHighRiskAndCapsAt100() {
    var r =
        new EvaluationRequest(
            "Phone",
            Category.ELECTRONICS,
            new BigDecimal("90000"),
            null,
            "Ad",
            true,
            20,
            false,
            false,
            "Today",
            true,
            false,
            true,
            true,
            true,
            EmotionalState.STRESSED,
            false,
            false,
            true);
    var result = service.calculate(r);
    assertEquals(100, result.score());
    assertEquals(RiskLevel.VERY_HIGH, result.risk());
    assertEquals("7 days", result.waiting());
    assertTrue(result.reasons().size() > 5);
  }

  @Test
  void rewardsPlannedNecessaryFrequentPurchase() {
    var r =
        new EvaluationRequest(
            "Shoes",
            Category.CLOTHING,
            new BigDecimal("2000"),
            null,
            null,
            false,
            0,
            true,
            true,
            "Several weeks",
            false,
            true,
            false,
            false,
            false,
            EmotionalState.NORMAL,
            true,
            true,
            false);
    var result = service.calculate(r);
    assertEquals(0, result.score());
    assertEquals(RiskLevel.LOW, result.risk());
  }
}
