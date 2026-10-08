package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnedItemRepository extends JpaRepository<OwnedItem, Long> {
  List<OwnedItem> findByUserIdOrderByProductName(Long id);

  long countByUserIdAndCategory(Long id, Enums.Category category);
}
