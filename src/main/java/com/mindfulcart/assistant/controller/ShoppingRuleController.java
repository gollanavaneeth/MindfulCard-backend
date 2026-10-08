package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.ShoppingRule;
import com.mindfulcart.assistant.repository.ShoppingRuleRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class ShoppingRuleController {
  private static final Set<String> TYPES =
      Set.of(
          "WAITING_PERIOD",
          "MAX_PRICE",
          "EMOTION_BLOCK",
          "LATE_NIGHT",
          "REQUIRE_COMPARISON",
          "CUSTOM");

  private final ShoppingRuleRepository rules;
  private final CurrentUserService current;

  public record RuleRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 600) String description,
      @NotBlank String ruleType,
      String category,
      @PositiveOrZero BigDecimal amountThreshold,
      @Min(1) @Max(365) Integer waitDays,
      String blockedEmotion,
      @Min(0) @Max(23) Integer startHour,
      @Min(0) @Max(23) Integer endHour,
      Boolean active) {}

  public record RuleResponse(
      Long id,
      String name,
      String description,
      String ruleType,
      String category,
      BigDecimal amountThreshold,
      Integer waitDays,
      String blockedEmotion,
      Integer startHour,
      Integer endHour,
      boolean active,
      java.time.LocalDateTime createdAt) {}

  public record RuleCheckRequest(
      String category,
      @NotNull @PositiveOrZero BigDecimal price,
      String emotionalState,
      Boolean planned,
      Boolean comparedAlternatives,
      @Min(0) @Max(23) Integer purchaseHour) {}

  public record RuleViolation(Long ruleId, String ruleName, String message, String severity) {}

  public record RuleCheckResponse(
      boolean allowed, int violationCount, List<RuleViolation> violations, int checkedRules) {}

  @GetMapping
  public List<RuleResponse> all() {
    return rules.findByUserIdOrderByCreatedAtDesc(current.get().getId()).stream()
        .map(this::dto)
        .toList();
  }

  @PostMapping
  public RuleResponse create(@Valid @RequestBody RuleRequest request) {
    ShoppingRule rule = new ShoppingRule();
    rule.setUser(current.get());
    copy(rule, request, true);
    return dto(rules.save(rule));
  }

  @PutMapping("/{id}")
  public RuleResponse update(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
    ShoppingRule rule = mine(id);
    copy(rule, request, false);
    return dto(rules.save(rule));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    rules.delete(mine(id));
  }

  @PostMapping("/check")
  public RuleCheckResponse check(@Valid @RequestBody RuleCheckRequest request) {
    List<ShoppingRule> active =
        rules.findByUserIdAndActiveTrueOrderByCreatedAtDesc(current.get().getId());
    List<RuleViolation> violations = new ArrayList<>();

    for (ShoppingRule rule : active) {
      if (!appliesToCategory(rule, request.category())) continue;

      RuleViolation violation =
          switch (rule.getRuleType()) {
            case "WAITING_PERIOD" ->
                Boolean.TRUE.equals(request.planned())
                    ? null
                    : violation(
                        rule,
                        "Wait " + value(rule.getWaitDays(), 1) + " day(s) before deciding.",
                        "WARNING");
            case "MAX_PRICE" ->
                rule.getAmountThreshold() != null
                        && request.price().compareTo(rule.getAmountThreshold()) > 0
                    ? violation(
                        rule,
                        "Price exceeds your limit of ₹"
                            + rule.getAmountThreshold().stripTrailingZeros().toPlainString()
                            + ".",
                        "BLOCK")
                    : null;
            case "EMOTION_BLOCK" ->
                same(rule.getBlockedEmotion(), request.emotionalState())
                    ? violation(
                        rule,
                        "This purchase conflicts with your "
                            + readable(rule.getBlockedEmotion())
                            + "-mood rule.",
                        "BLOCK")
                    : null;
            case "LATE_NIGHT" ->
                inHourRange(
                        value(request.purchaseHour(), java.time.LocalTime.now().getHour()),
                        value(rule.getStartHour(), 22),
                        value(rule.getEndHour(), 6))
                    ? violation(
                        rule,
                        "Pause this purchase until your no-shopping time window ends.",
                        "BLOCK")
                    : null;
            case "REQUIRE_COMPARISON" ->
                comparisonRequired(rule, request)
                    ? violation(rule, "Compare at least one alternative before buying.", "WARNING")
                    : null;
            case "CUSTOM" ->
                violation(
                    rule,
                    rule.getDescription() == null || rule.getDescription().isBlank()
                        ? "Review your personal shopping rule before continuing."
                        : rule.getDescription(),
                    "INFO");
            default -> null;
          };
      if (violation != null) violations.add(violation);
    }

    boolean allowed = violations.stream().noneMatch(v -> !"INFO".equals(v.severity()));
    return new RuleCheckResponse(allowed, violations.size(), violations, active.size());
  }

  private boolean comparisonRequired(ShoppingRule rule, RuleCheckRequest request) {
    boolean overThreshold =
        rule.getAmountThreshold() == null
            || request.price().compareTo(rule.getAmountThreshold()) >= 0;
    return overThreshold && !Boolean.TRUE.equals(request.comparedAlternatives());
  }

  private boolean appliesToCategory(ShoppingRule rule, String category) {
    return rule.getCategory() == null
        || rule.getCategory().isBlank()
        || "ALL".equalsIgnoreCase(rule.getCategory())
        || same(rule.getCategory(), category);
  }

  private boolean inHourRange(int hour, int start, int end) {
    if (start == end) return true;
    return start < end ? hour >= start && hour < end : hour >= start || hour < end;
  }

  private RuleViolation violation(ShoppingRule rule, String message, String severity) {
    return new RuleViolation(rule.getId(), rule.getName(), message, severity);
  }

  private void copy(ShoppingRule rule, RuleRequest request, boolean creating) {
    String type = normalize(request.ruleType());
    if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported rule type");
    if ("MAX_PRICE".equals(type) && request.amountThreshold() == null)
      throw new IllegalArgumentException("Amount threshold is required for a maximum-price rule");
    if ("EMOTION_BLOCK".equals(type) && blank(request.blockedEmotion()))
      throw new IllegalArgumentException("Blocked emotion is required for an emotion rule");

    rule.setName(request.name().trim());
    rule.setDescription(trimToNull(request.description()));
    rule.setRuleType(type);
    rule.setCategory(normalizeNullable(request.category()));
    rule.setAmountThreshold(request.amountThreshold());
    rule.setWaitDays(request.waitDays());
    rule.setBlockedEmotion(normalizeNullable(request.blockedEmotion()));
    rule.setStartHour(request.startHour());
    rule.setEndHour(request.endHour());
    rule.setActive(request.active() == null ? creating || rule.isActive() : request.active());
  }

  private ShoppingRule mine(Long id) {
    ShoppingRule rule =
        rules
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Shopping rule not found"));
    if (!rule.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Shopping rule not found");
    return rule;
  }

  private RuleResponse dto(ShoppingRule rule) {
    return new RuleResponse(
        rule.getId(),
        rule.getName(),
        rule.getDescription(),
        rule.getRuleType(),
        rule.getCategory(),
        rule.getAmountThreshold(),
        rule.getWaitDays(),
        rule.getBlockedEmotion(),
        rule.getStartHour(),
        rule.getEndHour(),
        rule.isActive(),
        rule.getCreatedAt());
  }

  private String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }

  private String normalizeNullable(String value) {
    return blank(value) ? null : normalize(value);
  }

  private String trimToNull(String value) {
    return blank(value) ? null : value.trim();
  }

  private boolean same(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String readable(String value) {
    return value == null ? "selected" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
  }

  private int value(Integer value, int fallback) {
    return value == null ? fallback : value;
  }
}
