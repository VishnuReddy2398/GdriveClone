package com.clone.drive.worker;

import com.clone.drive.config.RabbitMQConfig;
import com.clone.drive.domain.Notification;
import com.clone.drive.domain.User;
import com.clone.drive.event.NotificationEvent;
import com.clone.drive.repository.NotificationRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.WebSocketNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final WebSocketNotificationService webSocketNotificationService;

    public NotificationWorker(NotificationRepository notificationRepository,
                              UserRepository userRepository,
                              WebSocketNotificationService webSocketNotificationService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.webSocketNotificationService = webSocketNotificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATIONS)
    public void processNotification(NotificationEvent event) {
        log.info("Received notification event: {}", event);

        try {
            Optional<User> userOpt = userRepository.findById(event.getTargetUserId());
            if (userOpt.isEmpty()) {
                log.warn("Target user not found for notification: {}", event.getTargetUserId());
                return;
            }

            Notification notification = new Notification();
            notification.setUser(userOpt.get());
            notification.setType(event.getType());
            notification.setMessage(event.getMessage());
            notification.setResourceId(event.getResourceId());
            
            notificationRepository.save(notification);
            log.info("Notification saved for user {}", userOpt.get().getEmail());

            // Push real-time notification via WebSocket
            webSocketNotificationService.notifyUser(
                    userOpt.get().getId(),
                    event.getType(),
                    event.getMessage()
            );

        } catch (Exception e) {
            log.error("Failed to process notification event", e);
        }
    }
}
