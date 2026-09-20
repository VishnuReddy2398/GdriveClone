package com.clone.drive.repository;

import com.clone.drive.domain.Notification;
import com.clone.drive.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
    // Fetch unread notifications for a user, newest first
    List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user);
    
    // Fetch all notifications for a user (history), newest first
    List<Notification> findTop50ByUserOrderByCreatedAtDesc(User user);
}
