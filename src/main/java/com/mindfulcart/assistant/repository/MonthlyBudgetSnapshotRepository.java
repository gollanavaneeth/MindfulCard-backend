package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.MonthlyBudgetSnapshot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetSnapshotRepository
    extends JpaRepository<MonthlyBudgetSnapshot, Long> {
  List<MonthlyBudgetSnapshot> findByUserIdOrderBySnapshotYearDescSnapshotMonthDesc(Long userId);

  Optional<MonthlyBudgetSnapshot> findByUserIdAndSnapshotYearAndSnapshotMonth(
      Long userId, int year, int month);
}
