package com.clone.drive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a version of a file.
 * When a user re-uploads a file with the same name in the same folder,
 * a new version is created instead of overwriting — just like Google Drive.
 */
@Entity
@Table(name = "file_versions")
@Getter
@Setter
@NoArgsConstructor
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The logical file this version belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_record_id", nullable = false)
    private FileRecord fileRecord;

    private int versionNumber;

    private Long size;

    private String storageKey;

    private String sha256;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
