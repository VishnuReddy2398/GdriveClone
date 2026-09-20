package com.clone.drive.controller;

import com.clone.drive.domain.Notification;
import com.clone.drive.domain.User;
import com.clone.drive.repository.NotificationRepository;
import com.clone.drive.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        User user = getAuthenticatedUser();
        
        List<Notification> notifications;
        if (unreadOnly) {
            notifications = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);
        } else {
            notifications = notificationRepository.findTop50ByUserOrderByCreatedAtDesc(user);
        }
        
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<Notification> notifOpt = notificationRepository.findById(id);

        if (notifOpt.isPresent() && notifOpt.get().getUser().getId().equals(user.getId())) {
            Notification notif = notifOpt.get();
            notif.setRead(true);
            notificationRepository.save(notif);
        }

        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead() {
        User user = getAuthenticatedUser();
        List<Notification> unread = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);
        
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);

        return ResponseEntity.ok(Map.of("message", "All marked as read"));
    }
}
