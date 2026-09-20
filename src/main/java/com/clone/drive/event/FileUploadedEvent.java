package com.clone.drive.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Event published to RabbitMQ when a file is uploaded.
 * The async worker consumes this to generate thumbnails and scan for viruses.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadedEvent implements Serializable {
    private UUID fileRecordId;
    private String storageKey;
    private String mimeType;
    private String fileName;
    private UUID ownerId;
}
