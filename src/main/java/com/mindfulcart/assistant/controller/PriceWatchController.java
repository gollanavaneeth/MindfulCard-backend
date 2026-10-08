package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.Enums;
import com.mindfulcart.assistant.model.PriceObservation;
import com.mindfulcart.assistant.model.PriceWatch;
import com.mindfulcart.assistant.repository.PriceObservationRepository;
import com.mindfulcart.assistant.repository.PriceWatchRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/price-watches")
@RequiredArgsConstructor
public class PriceWatchController {
  private final PriceWatchRepository watches;
  private final PriceObservationRepository observations;
  private final CurrentUserService currentUser;

  @GetMapping
  public List<WatchResponse> all(@RequestParam(value = "active", required = false) Boolean active) {
    Long userId = currentUser.get().getId();
    List<PriceWatch> values =
        Boolean.TRUE.equals(active)
            ? watches.findByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId)
            : watches.findByUserIdOrderByUpdatedAtDesc(userId);
    return values.stream().map(this::response).toList();
  }

  @GetMapping("/alerts")
  public List<PriceAlert> alerts() {
    return watches.findByUserIdAndActiveTrueOrderByUpdatedAtDesc(currentUser.get().getId()).stream()
        .filter(value -> value.getCurrentPrice() != null)
        .map(this::alert)
        .filter(value -> !value.status().equals("TRACKING"))
        .toList();
  }

  @PostMapping
  @Transactional
  public WatchResponse create(@RequestBody WatchRequest request) {
    validateCreate(request);
    LocalDateTime now = LocalDateTime.now();
    PriceWatch watch =
        PriceWatch.builder()
            .user(currentUser.get())
            .productName(clean(request.productName(), 160))
            .category(request.category() == null ? Enums.Category.OTHER : request.category())
            .productUrl(url(request.productUrl()))
            .retailer(cleanNullable(request.retailer(), 120))
            .targetPrice(money(request.targetPrice(), "Target price"))
            .currentPrice(optionalMoney(request.currentPrice(), "Current price"))
            .active(request.active() == null || request.active())
            .lastObservedAt(request.currentPrice() == null ? null : now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    watch = watches.save(watch);
    if (watch.getCurrentPrice() != null)
      saveObservation(
          watch, watch.getCurrentPrice(), watch.getRetailer(), watch.getProductUrl(), now);
    return response(watch);
  }

  @PutMapping("/{id}")
  @Transactional
  public WatchResponse update(@PathVariable Long id, @RequestBody WatchRequest request) {
    PriceWatch watch = owned(id);
    if (request.productName() != null) watch.setProductName(clean(request.productName(), 160));
    if (request.category() != null) watch.setCategory(request.category());
    if (request.productUrl() != null) watch.setProductUrl(url(request.productUrl()));
    if (request.retailer() != null) watch.setRetailer(cleanNullable(request.retailer(), 120));
    if (request.targetPrice() != null)
      watch.setTargetPrice(money(request.targetPrice(), "Target price"));
    if (request.active() != null) watch.setActive(request.active());
    if (request.currentPrice() != null) {
      BigDecimal price = money(request.currentPrice(), "Current price");
      watch.setCurrentPrice(price);
      watch.setLastObservedAt(LocalDateTime.now());
      saveObservation(
          watch, price, watch.getRetailer(), watch.getProductUrl(), watch.getLastObservedAt());
    }
    watch.setUpdatedAt(LocalDateTime.now());
    return response(watches.save(watch));
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@PathVariable Long id) {
    watches.delete(owned(id));
  }

  @PostMapping("/{id}/observations")
  @Transactional
  public WatchResponse observe(@PathVariable Long id, @RequestBody ObservationRequest request) {
    PriceWatch watch = owned(id);
    BigDecimal price = money(request.price(), "Observed price");
    String retailer =
        request.retailer() == null ? watch.getRetailer() : cleanNullable(request.retailer(), 120);
    String productUrl =
        request.productUrl() == null ? watch.getProductUrl() : url(request.productUrl());
    LocalDateTime observedAt =
        request.observedAt() == null ? LocalDateTime.now() : request.observedAt();
    if (observedAt.isAfter(LocalDateTime.now().plusMinutes(5)))
      throw new IllegalArgumentException("Observation date cannot be in the future");
    saveObservation(watch, price, retailer, productUrl, observedAt);
    if (watch.getLastObservedAt() == null || !observedAt.isBefore(watch.getLastObservedAt())) {
      watch.setCurrentPrice(price);
      watch.setLastObservedAt(observedAt);
      if (retailer != null) watch.setRetailer(retailer);
      if (productUrl != null) watch.setProductUrl(productUrl);
    }
    watch.setUpdatedAt(LocalDateTime.now());
    return response(watches.save(watch));
  }

  @GetMapping("/{id}/history")
  public List<ObservationResponse> history(@PathVariable Long id) {
    PriceWatch watch = owned(id);
    return observations.findByWatchIdOrderByObservedAtDesc(watch.getId()).stream()
        .map(this::observation)
        .toList();
  }

  private void saveObservation(
      PriceWatch watch,
      BigDecimal price,
      String retailer,
      String productUrl,
      LocalDateTime observedAt) {
    observations.save(
        PriceObservation.builder()
            .watch(watch)
            .price(price)
            .retailer(retailer)
            .productUrl(productUrl)
            .observedAt(observedAt)
            .build());
  }

  private PriceWatch owned(Long id) {
    PriceWatch watch =
        watches
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Price watch not found"));
    if (!watch.getUser().getId().equals(currentUser.get().getId()))
      throw new IllegalArgumentException("Price watch not found");
    return watch;
  }

  private WatchResponse response(PriceWatch watch) {
    BigDecimal best = observations.minimumPrice(watch.getId());
    long count = observations.countByWatchId(watch.getId());
    boolean reached =
        watch.getCurrentPrice() != null
            && watch.getCurrentPrice().compareTo(watch.getTargetPrice()) <= 0;
    return new WatchResponse(
        watch.getId(),
        watch.getProductName(),
        watch.getCategory(),
        watch.getProductUrl(),
        watch.getRetailer(),
        watch.getTargetPrice(),
        watch.getCurrentPrice(),
        best,
        reached,
        watch.isActive(),
        alert(watch).status(),
        watch.getLastObservedAt(),
        watch.getCreatedAt(),
        watch.getUpdatedAt(),
        count);
  }

  private PriceAlert alert(PriceWatch watch) {
    BigDecimal current = watch.getCurrentPrice();
    String status = "TRACKING";
    if (current != null && current.compareTo(watch.getTargetPrice()) <= 0)
      status = "TARGET_REACHED";
    else if (current != null
        && current.compareTo(watch.getTargetPrice().multiply(new BigDecimal("1.10"))) <= 0)
      status = "NEAR_TARGET";
    BigDecimal difference =
        current == null
            ? BigDecimal.ZERO
            : current.subtract(watch.getTargetPrice()).setScale(2, RoundingMode.HALF_UP);
    String message =
        status.equals("TARGET_REACHED")
            ? watch.getProductName()
                + " has reached your target price. Re-evaluate the need before purchasing."
            : status.equals("NEAR_TARGET")
                ? watch.getProductName() + " is within 10% of your target price."
                : "Tracking " + watch.getProductName() + ".";
    return new PriceAlert(
        watch.getId(),
        watch.getProductName(),
        current,
        watch.getTargetPrice(),
        difference,
        status,
        message);
  }

  private ObservationResponse observation(PriceObservation item) {
    return new ObservationResponse(
        item.getId(),
        item.getPrice(),
        item.getRetailer(),
        item.getProductUrl(),
        item.getObservedAt(),
        item.getCreatedAt());
  }

  private void validateCreate(WatchRequest request) {
    if (request == null) throw new IllegalArgumentException("Price watch details are required");
    clean(request.productName(), 160);
    money(request.targetPrice(), "Target price");
    optionalMoney(request.currentPrice(), "Current price");
  }

  private BigDecimal money(BigDecimal value, String label) {
    if (value == null || value.signum() <= 0)
      throw new IllegalArgumentException(label + " must be greater than zero");
    if (value.compareTo(new BigDecimal("999999999999.99")) > 0)
      throw new IllegalArgumentException(label + " is too large");
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal optionalMoney(BigDecimal value, String label) {
    return value == null ? null : money(value, label);
  }

  private String clean(String value, int maxLength) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Product name is required");
    String result = value.trim();
    if (result.length() > maxLength) throw new IllegalArgumentException("Product name is too long");
    return result;
  }

  private String cleanNullable(String value, int maxLength) {
    if (value == null || value.isBlank()) return null;
    String result = value.trim();
    if (result.length() > maxLength) throw new IllegalArgumentException("Value is too long");
    return result;
  }

  private String url(String value) {
    if (value == null || value.isBlank()) return null;
    String result = value.trim();
    if (result.length() > 1_000) throw new IllegalArgumentException("Product URL is too long");
    try {
      URI uri = URI.create(result);
      if (uri.getScheme() == null
          || !(uri.getScheme().equalsIgnoreCase("http")
              || uri.getScheme().equalsIgnoreCase("https"))) {
        throw new IllegalArgumentException("Product URL must start with http:// or https://");
      }
      return result;
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null && exception.getMessage().startsWith("Product URL"))
        throw exception;
      throw new IllegalArgumentException("Product URL is invalid");
    }
  }

  public record WatchRequest(
      String productName,
      Enums.Category category,
      String productUrl,
      String retailer,
      BigDecimal targetPrice,
      BigDecimal currentPrice,
      Boolean active) {}

  public record ObservationRequest(
      BigDecimal price, String retailer, String productUrl, LocalDateTime observedAt) {}

  public record WatchResponse(
      Long id,
      String productName,
      Enums.Category category,
      String productUrl,
      String retailer,
      BigDecimal targetPrice,
      BigDecimal currentPrice,
      BigDecimal bestObservedPrice,
      boolean targetReached,
      boolean active,
      String alertStatus,
      LocalDateTime lastObservedAt,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      long observationCount) {}

  public record ObservationResponse(
      Long id,
      BigDecimal price,
      String retailer,
      String productUrl,
      LocalDateTime observedAt,
      LocalDateTime createdAt) {}

  public record PriceAlert(
      Long watchId,
      String productName,
      BigDecimal currentPrice,
      BigDecimal targetPrice,
      BigDecimal amountAboveTarget,
      String status,
      String message) {}
}
