package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.SavingsGoal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
  List<SavingsGoal> findByUserIdOrderByCreatedAtDesc(Long id);
}
