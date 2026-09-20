package com.clone.drive.worker;

import com.clone.drive.config.RabbitMQConfig;
import com.clone.drive.domain.FileRecord;
import com.clone.drive.event.FileUploadedEvent;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;

/**
 * Async worker that listens for FileUploaded events from RabbitMQ.
 * Generates thumbnails for image files.
 *
 * In a production microservice architecture, this would be a SEPARATE
 * Spring Boot application. Here it runs in the same JVM for simplicity,
 * but is fully decoupled via the message queue.
 */
@Component
public class ThumbnailWorker {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailWorker.class);
    private static final int THUMB_WIDTH = 200;
    private static final int THUMB_HEIGHT = 200;

    private final FileRecordRepository fileRecordRepository;
    private final StorageService storageService;

    public ThumbnailWorker(FileRecordRepository fileRecordRepository, StorageService storageService) {
        this.fileRecordRepository = fileRecordRepository;
        this.storageService = storageService;
    }

    @RabbitListener(queues = RabbitMQConfig.FILE_UPLOADED_QUEUE)
    public void handleFileUploaded(FileUploadedEvent event) {
        log.info("Worker received FileUploaded event: {}", event.getFileName());

        // Only generate thumbnails for images
        if (event.getMimeType() == null || !event.getMimeType().startsWith("image/")) {
            log.info("Skipping thumbnail generation for non-image file: {}", event.getMimeType());
            return;
        }

        try {
            // 1. Load the original image from storage
            Resource resource = storageService.loadAsResource(event.getStorageKey());
            InputStream inputStream = resource.getInputStream();
            BufferedImage originalImage = ImageIO.read(inputStream);

            if (originalImage == null) {
                log.warn("Could not read image: {}", event.getStorageKey());
                return;
            }

            // 2. Generate thumbnail using Java's built-in Graphics2D
            BufferedImage thumbnail = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = thumbnail.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(originalImage, 0, 0, THUMB_WIDTH, THUMB_HEIGHT, null);
            g2d.dispose();

            // 3. Convert thumbnail to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", baos);
            byte[] thumbBytes = baos.toByteArray();

            // 4. Upload thumbnail to storage under a predictable key
            String thumbKey = event.getStorageKey() + "_thumb.jpg";
            // For local storage, we write directly since we have the bytes
            java.nio.file.Path thumbPath = java.nio.file.Paths.get("./data/storage", thumbKey);
            java.nio.file.Files.createDirectories(thumbPath.getParent());
            java.nio.file.Files.write(thumbPath, thumbBytes);

            log.info("Thumbnail generated successfully: {}", thumbKey);

            // 5. Update the FileRecord with the thumbnail URL (optional field you can add later)
            // For now, the thumbnail is accessible at the predictable key pattern.

        } catch (UnsupportedOperationException e) {
            // R2 mode: loadAsResource is not supported, would need to download via S3Client
            log.info("Thumbnail generation skipped in R2 mode (would require S3Client download)");
        } catch (Exception e) {
            log.error("Failed to generate thumbnail for: {}", event.getFileName(), e);
        }
    }
}
