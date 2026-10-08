package com.mindfulcart.assistant.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes the retired demo workspace and all of its dependent rows. */
@Component
@Order(0)
@RequiredArgsConstructor
public class LegacyDemoCleanup implements CommandLineRunner {
  private static final String DEMO_EMAIL = "demo@mindfulcart.app";
  private final JdbcTemplate jdbc;

  @Override
  @Transactional
  public void run(String... args) {
    List<Long> ids =
        jdbc.query(
            "select id from users where lower(email)=?", (rs, row) -> rs.getLong(1), DEMO_EMAIL);
    for (Long userId : ids) remove(userId);
  }

  private void remove(Long id) {
    jdbc.update(
        "delete from price_observation where watch_id in (select id from price_watch where user_id=?)",
        id);
    jdbc.update(
        "delete from reassessment where user_id=? or evaluation_id in (select id from evaluation where user_id=?)",
        id,
        id);
    for (String table :
        List.of(
            "account_token",
            "app_notifications",
            "category_budgets",
            "monthly_budget_snapshots",
            "shopping_rules",
            "subscriptions",
            "price_watch",
            "budget",
            "owned_item",
            "savings_goal",
            "wishlist_item",
            "evaluation")) {
      jdbc.update("delete from " + table + " where user_id=?", id);
    }
    jdbc.update("delete from users where id=?", id);
  }
}
