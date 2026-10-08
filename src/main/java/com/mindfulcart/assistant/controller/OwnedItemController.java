package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.dto.ApiDtos.*;
import com.mindfulcart.assistant.model.OwnedItem;
import com.mindfulcart.assistant.repository.OwnedItemRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owned-items")
@RequiredArgsConstructor
public class OwnedItemController {
  private final OwnedItemRepository repo;
  private final CurrentUserService current;

  @GetMapping
  public List<OwnedItemResponse> all(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String category) {
    return repo.findByUserIdOrderByProductName(current.get().getId()).stream()
        .filter(
            x -> search == null || x.getProductName().toLowerCase().contains(search.toLowerCase()))
        .filter(
            x ->
                category == null
                    || category.isBlank()
                    || x.getCategory().name().equalsIgnoreCase(category))
        .map(this::dto)
        .toList();
  }

  @PostMapping
  OwnedItemResponse add(@Valid @RequestBody OwnedItemRequest r) {
    OwnedItem x = new OwnedItem();
    x.setUser(current.get());
    copy(x, r);
    return dto(repo.save(x));
  }

  @PutMapping("/{id}")
  OwnedItemResponse update(@PathVariable Long id, @Valid @RequestBody OwnedItemRequest r) {
    OwnedItem x = mine(id);
    copy(x, r);
    return dto(repo.save(x));
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable Long id) {
    repo.delete(mine(id));
  }

  private OwnedItem mine(Long id) {
    OwnedItem x =
        repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Item not found"));
    if (!x.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Item not found");
    return x;
  }

  private void copy(OwnedItem x, OwnedItemRequest r) {
    x.setProductName(r.productName());
    x.setCategory(r.category());
    x.setPurchasePrice(r.purchasePrice());
    x.setPurchaseDate(r.purchaseDate());
    x.setConditionName(r.conditionName());
    x.setUsageFrequency(r.usageFrequency());
  }

  private OwnedItemResponse dto(OwnedItem x) {
    return new OwnedItemResponse(
        x.getId(),
        x.getProductName(),
        x.getCategory(),
        x.getPurchasePrice(),
        x.getPurchaseDate(),
        x.getConditionName(),
        x.getUsageFrequency());
  }
}
