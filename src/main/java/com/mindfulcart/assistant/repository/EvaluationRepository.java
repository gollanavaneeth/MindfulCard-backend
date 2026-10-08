package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.Evaluation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
  List<Evaluation> findByUserIdOrderByCreatedAtDesc(Long id);
}
