package com.clone.drive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Activity log entry for tracking user actions (like Google Drive's activity panel).
 */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Action types: UPLOAD, DOWNLOAD, DELETE, RESTORE, RENAME, MOVE, SHARE, STAR, UNSTAR
    @Column(nullable = false)
    private String action;

    // Human-readable description
    @Column(nullable = false)
    private String description;

    // Optional reference to the file involved
    private UUID targetFileId;

    // Optional reference to the folder involved
    private UUID targetFolderId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
