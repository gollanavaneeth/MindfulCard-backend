package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.WishlistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
  List<WishlistItem> findByUserIdOrderByAddedDateDesc(Long id);
}
