package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.Reassessment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReassessmentRepository extends JpaRepository<Reassessment, Long> {
  List<Reassessment> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<Reassessment> findByEvaluationIdAndUserIdOrderByCreatedAtDesc(
      Long evaluationId, Long userId);
}
