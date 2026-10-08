package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.PriceObservation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {
  List<PriceObservation> findByWatchIdOrderByObservedAtDesc(Long watchId);

  long countByWatchId(Long watchId);

  @Query("select min(o.price) from PriceObservation o where o.watch.id = :watchId")
  BigDecimal minimumPrice(@Param("watchId") Long watchId);
}
