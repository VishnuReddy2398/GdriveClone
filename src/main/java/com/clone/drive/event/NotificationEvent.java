package com.clone.drive.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Generic event object sent to RabbitMQ for generating notifications.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent implements Serializable {
    private UUID targetUserId;
    private String type;
    private String message;
    private UUID resourceId;
}
