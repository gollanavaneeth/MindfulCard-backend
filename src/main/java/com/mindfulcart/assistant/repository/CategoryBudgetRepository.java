package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.CategoryBudget;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, Long> {
  List<CategoryBudget> findByUserIdOrderByCategoryAsc(Long userId);

  Optional<CategoryBudget> findByUserIdAndCategoryIgnoreCase(Long userId, String category);
}
