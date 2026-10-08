package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.Enums;
import com.mindfulcart.assistant.model.Evaluation;
import com.mindfulcart.assistant.model.User;
import com.mindfulcart.assistant.repository.EvaluationRepository;
import com.mindfulcart.assistant.repository.PriceWatchRepository;
import com.mindfulcart.assistant.repository.UserRepository;
import com.mindfulcart.assistant.repository.WishlistRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMetricsController {
  private final UserRepository users;
  private final EvaluationRepository evaluations;
  private final WishlistRepository wishlist;
  private final PriceWatchRepository priceWatches;

  @GetMapping("/metrics")
  @Transactional(readOnly = true)
  public AdminMetrics metrics() {
    List<User> allUsers = users.findAll();
    List<Evaluation> all = evaluations.findAll();
    LocalDate thirtyDaysAgo = LocalDate.now().minusDays(29);
    YearMonth currentMonth = YearMonth.now();

    Set<Long> activeUserIds =
        all.stream().map(item -> item.getUser().getId()).collect(Collectors.toSet());
    long recentEvaluations =
        all.stream()
            .filter(
                item ->
                    item.getCreatedAt() != null
                        && !item.getCreatedAt().toLocalDate().isBefore(thirtyDaysAgo))
            .count();
    long newUsersThisMonth =
        allUsers.stream()
            .filter(
                user ->
                    user.getCreatedAt() != null
                        && YearMonth.from(user.getCreatedAt()).equals(currentMonth))
            .count();
    long avoided =
        all.stream().filter(item -> decision(item) == Enums.PurchaseDecision.AVOIDED).count();
    double averageScore =
        round(all.stream().mapToInt(Evaluation::getImpulseScore).average().orElse(0));
    double avoidanceRate = all.isEmpty() ? 0 : round(avoided * 100.0 / all.size());
    BigDecimal protectedAmount =
        all.stream()
            .filter(item -> decision(item) == Enums.PurchaseDecision.AVOIDED)
            .map(Evaluation::getPrice)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, Long> decisions =
        frequency(all.stream().map(item -> decision(item).name()).toList());
    Map<String, Long> categories =
        frequency(
            all.stream()
                .map(item -> item.getCategory() == null ? "OTHER" : item.getCategory().name())
                .toList());
    Map<String, Long> risks =
        frequency(
            all.stream()
                .map(item -> item.getRiskLevel() == null ? "UNKNOWN" : item.getRiskLevel().name())
                .toList());
    Map<String, Long> roles =
        frequency(allUsers.stream().map(user -> normalizedRole(user.getRole())).toList());
    Map<String, Long> monthlyEvaluations =
        sortedByMonth(
            all.stream()
                .filter(item -> item.getCreatedAt() != null)
                .collect(
                    Collectors.groupingBy(
                        item -> YearMonth.from(item.getCreatedAt()).toString(),
                        Collectors.counting())));
    Map<String, Long> monthlySignups =
        sortedByMonth(
            allUsers.stream()
                .filter(user -> user.getCreatedAt() != null)
                .collect(
                    Collectors.groupingBy(
                        user -> YearMonth.from(user.getCreatedAt()).toString(),
                        Collectors.counting())));

    return new AdminMetrics(
        allUsers.size(),
        activeUserIds.size(),
        newUsersThisMonth,
        all.size(),
        recentEvaluations,
        wishlist.count(),
        priceWatches.count(),
        averageScore,
        avoidanceRate,
        protectedAmount,
        decisions,
        categories,
        risks,
        roles,
        monthlyEvaluations,
        monthlySignups,
        "User activity is summarized for administration. Passwords and questionnaire answers are never exposed.");
  }

  @GetMapping("/users")
  @Transactional(readOnly = true)
  public List<AdminUser> users() {
    List<Evaluation> allEvaluations = evaluations.findAll();
    return users.findAllByOrderByCreatedAtDesc().stream()
        .map(
            user -> {
              List<Evaluation> activity =
                  allEvaluations.stream()
                      .filter(item -> item.getUser().getId().equals(user.getId()))
                      .toList();
              long avoided =
                  activity.stream()
                      .filter(item -> decision(item) == Enums.PurchaseDecision.AVOIDED)
                      .count();
              BigDecimal protectedAmount =
                  activity.stream()
                      .filter(item -> decision(item) == Enums.PurchaseDecision.AVOIDED)
                      .map(Evaluation::getPrice)
                      .filter(Objects::nonNull)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              LocalDateTime lastActivity =
                  activity.stream()
                      .map(Evaluation::getCreatedAt)
                      .filter(Objects::nonNull)
                      .max(LocalDateTime::compareTo)
                      .orElse(null);
              return new AdminUser(
                  user.getId(),
                  user.getFullName(),
                  user.getEmail(),
                  normalizedRole(user.getRole()),
                  user.isEmailVerified(),
                  user.getCreatedAt(),
                  activity.size(),
                  avoided,
                  protectedAmount,
                  lastActivity);
            })
        .toList();
  }

  @PutMapping("/users/{id}/role")
  @Transactional
  public AdminUser updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
    User user =
        users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    String requested = request.role().toUpperCase(Locale.ROOT);
    if ("ADMIN".equalsIgnoreCase(user.getRole())
        && "USER".equals(requested)
        && users.countByRoleIgnoreCase("ADMIN") <= 1) {
      throw new IllegalArgumentException("At least one administrator must remain");
    }
    user.setRole(requested);
    users.save(user);
    return users().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
  }

  private Enums.PurchaseDecision decision(Evaluation item) {
    return item.getDecision() == null ? Enums.PurchaseDecision.PENDING : item.getDecision();
  }

  private String normalizedRole(String role) {
    return role == null || role.isBlank() ? "USER" : role.toUpperCase(Locale.ROOT);
  }

  private Map<String, Long> frequency(List<String> values) {
    return values.stream()
        .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private Map<String, Long> sortedByMonth(Map<String, Long> values) {
    return values.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  public record RoleRequest(
      @NotBlank @Pattern(regexp = "USER|ADMIN", flags = Pattern.Flag.CASE_INSENSITIVE)
          String role) {}

  public record AdminUser(
      Long id,
      String fullName,
      String email,
      String role,
      boolean emailVerified,
      LocalDateTime createdAt,
      int evaluationCount,
      long avoidedCount,
      BigDecimal protectedAmount,
      LocalDateTime lastActivity) {}

  public record AdminMetrics(
      long totalUsers,
      long usersWithEvaluations,
      long newUsersThisMonth,
      long totalEvaluations,
      long evaluationsLast30Days,
      long wishlistItems,
      long priceWatches,
      double averageImpulseScore,
      double avoidanceRate,
      BigDecimal protectedAmount,
      Map<String, Long> decisions,
      Map<String, Long> categories,
      Map<String, Long> risks,
      Map<String, Long> roles,
      Map<String, Long> monthlyEvaluations,
      Map<String, Long> monthlySignups,
      String privacyNote) {}
}
