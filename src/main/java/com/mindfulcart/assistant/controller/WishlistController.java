package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.dto.ApiDtos.*;
import com.mindfulcart.assistant.model.*;
import com.mindfulcart.assistant.repository.WishlistRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {
  private final WishlistRepository repo;
  private final CurrentUserService current;

  @GetMapping
  List<WishlistResponse> all() {
    return repo.findByUserIdOrderByAddedDateDesc(current.get().getId()).stream()
        .map(this::dto)
        .toList();
  }

  @PostMapping
  WishlistResponse add(@Valid @RequestBody WishlistRequest r) {
    WishlistItem x = new WishlistItem();
    x.setUser(current.get());
    x.setAddedDate(java.time.LocalDateTime.now());
    copy(x, r);
    return dto(repo.save(x));
  }

  @PutMapping("/{id}")
  WishlistResponse update(@PathVariable Long id, @Valid @RequestBody WishlistRequest r) {
    var x = mine(id);
    copy(x, r);
    return dto(repo.save(x));
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable Long id) {
    repo.delete(mine(id));
  }

  private WishlistItem mine(Long id) {
    var x =
        repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Wishlist item not found"));
    if (!x.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Wishlist item not found");
    return x;
  }

  private void copy(WishlistItem x, WishlistRequest r) {
    x.setProductName(r.productName());
    x.setCategory(r.category());
    x.setPrice(r.price());
    x.setWaitingUntil(r.waitingUntil());
    x.setStatus(r.status() == null ? Enums.WishlistStatus.WAITING : r.status());
  }

  private WishlistResponse dto(WishlistItem x) {
    return new WishlistResponse(
        x.getId(),
        x.getProductName(),
        x.getCategory(),
        x.getPrice(),
        x.getAddedDate(),
        x.getWaitingUntil(),
        x.getStatus());
  }
}
