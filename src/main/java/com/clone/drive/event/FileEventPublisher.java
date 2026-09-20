package com.clone.drive.event;

import com.clone.drive.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes file events to RabbitMQ.
 * Called from FileController after a successful upload.
 */
@Service
public class FileEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FileEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public FileEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishFileUploaded(FileUploadedEvent event) {
        log.info("Publishing FileUploaded event for file: {}", event.getFileName());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.FILE_UPLOADED_ROUTING_KEY,
                event
        );
    }
}
