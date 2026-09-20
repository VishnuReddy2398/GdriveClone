package com.clone.drive.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service to push real-time events to connected WebSocket clients.
 * Components call this service to notify users instantly when something changes.
 */
@Service
public class WebSocketNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketNotificationService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Send a notification to a specific user.
     * The frontend subscribes to /topic/user.{userId}.notifications
     */
    public void notifyUser(UUID userId, String type, String message) {
        String destination = "/topic/user." + userId + ".notifications";
        log.info("Pushing WebSocket notification to {}: {}", destination, message);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend(destination, (Object) payload);
    }

    /**
     * Send a file-change event to a specific user.
     * The frontend subscribes to /topic/user.{userId}.drive
     */
    public void notifyDriveUpdate(UUID userId, String action, Map<String, Object> payload) {
        String destination = "/topic/user." + userId + ".drive";
        log.info("Pushing drive update to {}: action={}", destination, action);
        payload.put("action", action);
        payload.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend(destination, (Object) payload);
    }
}
