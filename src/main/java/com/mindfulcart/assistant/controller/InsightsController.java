package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.service.CurrentUserService;
import com.mindfulcart.assistant.service.MonthlyInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {
  private final MonthlyInsightsService insights;
  private final CurrentUserService currentUser;

  @GetMapping("/monthly")
  public MonthlyInsightsService.MonthlySummary monthly(
      @RequestParam(value = "month", required = false) String month) {
    return insights.summarize(currentUser.get().getId(), insights.parseMonth(month));
  }
}
