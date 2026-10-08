package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.Budget;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
  Optional<Budget> findByUserId(Long id);
}
