package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.Subscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  List<Subscription> findByUserIdOrderByNextRenewalDateAsc(Long userId);
}
