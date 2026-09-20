package com.clone.drive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a file's metadata in the drive.
 * The actual file contents are stored in Cloudflare R2.
 */
@Entity
@Table(name = "file_records")
@Getter
@Setter
@NoArgsConstructor
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String mimeType;

    private Long size; // Size in bytes

    // The user who owns this file
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // The folder this file is located in. Null means root directory.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    // The key/path used to store this object in Cloudflare R2
    @Column(nullable = false)
    private String storageKey;

    // Used for deduplication
    private String sha256;

    // Star/Favorite flag
    private boolean starred = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Track last access for "Recent files" feature
    private LocalDateTime lastAccessedAt;

    // Soft delete flag
    private LocalDateTime deletedAt;

    // --- Envelope Encryption Fields ---
    // The Data Encryption Key (DEK) wrapped/encrypted with the Master KEK
    @Column(length = 1024)
    private String wrappedDek;

    // The Initialization Vector (IV) used during AES encryption of the file
    @Column(length = 255)
    private String iv;
}
