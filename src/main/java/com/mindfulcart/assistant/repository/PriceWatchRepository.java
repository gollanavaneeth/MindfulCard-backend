package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.PriceWatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceWatchRepository extends JpaRepository<PriceWatch, Long> {
  List<PriceWatch> findByUserIdOrderByUpdatedAtDesc(Long userId);

  List<PriceWatch> findByUserIdAndActiveTrueOrderByUpdatedAtDesc(Long userId);
}
