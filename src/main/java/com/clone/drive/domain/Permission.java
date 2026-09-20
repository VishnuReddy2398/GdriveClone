package com.clone.drive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Permission entity for resource-level access control.
 * Answers: "Can user X perform operation Y on resource Z?"
 * 
 * Permission levels (ordered by access):
 *   VIEWER    — can view/download only
 *   COMMENTER — can view + comment
 *   EDITOR    — can view + comment + edit/upload new versions
 *   OWNER     — full control including delete, share, transfer
 */
@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "file_record_id"}),
    @UniqueConstraint(columnNames = {"user_id", "folder_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The user being granted access
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Target resource — exactly one of these must be set
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_record_id")
    private FileRecord fileRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    // OWNER, EDITOR, COMMENTER, VIEWER
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PermissionLevel level;

    // Who granted this permission
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PermissionLevel {
        VIEWER(1),
        COMMENTER(2),
        EDITOR(3),
        OWNER(4);

        private final int rank;

        PermissionLevel(int rank) {
            this.rank = rank;
        }

        public int getRank() {
            return rank;
        }

        public boolean hasAtLeast(PermissionLevel required) {
            return this.rank >= required.rank;
        }
    }
}
