package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.ShoppingRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingRuleRepository extends JpaRepository<ShoppingRule, Long> {
  List<ShoppingRule> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<ShoppingRule> findByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);
}
