package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.AppNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
  List<AppNotification> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<AppNotification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

  long countByUserIdAndReadFalse(Long userId);
}
