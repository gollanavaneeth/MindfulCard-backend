package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.AppNotification;
import com.mindfulcart.assistant.repository.AppNotificationRepository;
import com.mindfulcart.assistant.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final AppNotificationRepository notifications;
  private final CurrentUserService current;

  public record NotificationRequest(
      @NotBlank @Size(max = 40) String type,
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 1200) String message,
      @Size(max = 500) String actionUrl) {}

  public record NotificationResponse(
      Long id,
      String type,
      String title,
      String message,
      String actionUrl,
      boolean read,
      LocalDateTime createdAt,
      LocalDateTime readAt) {}

  public record CountResponse(long count) {}

  @GetMapping
  public List<NotificationResponse> all(@RequestParam(defaultValue = "false") boolean unreadOnly) {
    Long userId = current.get().getId();
    List<AppNotification> items =
        unreadOnly
            ? notifications.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
            : notifications.findByUserIdOrderByCreatedAtDesc(userId);
    return items.stream().map(this::dto).toList();
  }

  @GetMapping("/unread-count")
  public CountResponse unreadCount() {
    return new CountResponse(notifications.countByUserIdAndReadFalse(current.get().getId()));
  }

  @PostMapping
  public NotificationResponse create(@Valid @RequestBody NotificationRequest request) {
    AppNotification notification = new AppNotification();
    notification.setUser(current.get());
    copy(notification, request);
    return dto(notifications.save(notification));
  }

  @PutMapping("/{id}")
  public NotificationResponse update(
      @PathVariable Long id, @Valid @RequestBody NotificationRequest request) {
    AppNotification notification = mine(id);
    copy(notification, request);
    return dto(notifications.save(notification));
  }

  @PutMapping("/{id}/read")
  public NotificationResponse markRead(@PathVariable Long id) {
    AppNotification notification = mine(id);
    if (!notification.isRead()) {
      notification.setRead(true);
      notification.setReadAt(LocalDateTime.now());
    }
    return dto(notifications.save(notification));
  }

  @PutMapping("/read-all")
  public CountResponse markAllRead() {
    List<AppNotification> unread =
        notifications.findByUserIdAndReadFalseOrderByCreatedAtDesc(current.get().getId());
    LocalDateTime now = LocalDateTime.now();
    unread.forEach(
        item -> {
          item.setRead(true);
          item.setReadAt(now);
        });
    notifications.saveAll(unread);
    return new CountResponse(unread.size());
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    notifications.delete(mine(id));
  }

  @DeleteMapping("/read")
  public CountResponse deleteRead() {
    List<AppNotification> read =
        notifications.findByUserIdOrderByCreatedAtDesc(current.get().getId()).stream()
            .filter(AppNotification::isRead)
            .toList();
    notifications.deleteAll(read);
    return new CountResponse(read.size());
  }

  private void copy(AppNotification notification, NotificationRequest request) {
    notification.setType(request.type().trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    notification.setTitle(request.title().trim());
    notification.setMessage(request.message().trim());
    notification.setActionUrl(
        request.actionUrl() == null || request.actionUrl().isBlank()
            ? null
            : request.actionUrl().trim());
  }

  private AppNotification mine(Long id) {
    AppNotification notification =
        notifications
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    if (!notification.getUser().getId().equals(current.get().getId()))
      throw new IllegalArgumentException("Notification not found");
    return notification;
  }

  private NotificationResponse dto(AppNotification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getMessage(),
        notification.getActionUrl(),
        notification.isRead(),
        notification.getCreatedAt(),
        notification.getReadAt());
  }
}
