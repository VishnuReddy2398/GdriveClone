package com.clone.drive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a secure, shareable link for a file or folder.
 */
@Entity
@Table(name = "share_links")
@Getter
@Setter
@NoArgsConstructor
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The unique, unguessable token part of the URL (e.g. drive.com/share/{token})
    @Column(nullable = false, unique = true)
    private String token;

    // Optional: Only one of these will be set depending on what is shared
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_record_id")
    private FileRecord fileRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    // 'VIEW' or 'EDIT' (if you plan to support collaborative editing later)
    @Column(nullable = false)
    private String permissionLevel = "VIEW";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime expiresAt; // Null means it never expires
}
