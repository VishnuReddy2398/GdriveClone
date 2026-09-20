package com.clone.drive.repository;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRecordRepository extends JpaRepository<FileRecord, UUID> {
    
    // Find files in a specific folder belonging to a user
    List<FileRecord> findByOwnerAndFolderAndDeletedAtIsNull(User owner, Folder folder);
    
    // Find files in the root directory (folder is null) belonging to a user
    List<FileRecord> findByOwnerAndFolderIsNullAndDeletedAtIsNull(User owner);
    
    // Find an existing record by SHA256 hash to enable deduplication
    Optional<FileRecord> findFirstBySha256(String sha256);
    
    // Find trashed files belonging to a user
    List<FileRecord> findByOwnerAndDeletedAtIsNotNull(User owner);
    
    // Starred/favorite files
    List<FileRecord> findByOwnerAndStarredTrueAndDeletedAtIsNull(User owner);
    
    // Recent files ordered by last accessed
    List<FileRecord> findTop20ByOwnerAndDeletedAtIsNullOrderByLastAccessedAtDesc(User owner);
    
    // Filter by mime type prefix (e.g., "image/%", "video/%")
    @org.springframework.data.jpa.repository.Query("SELECT f FROM FileRecord f WHERE f.owner = :owner AND f.deletedAt IS NULL AND f.mimeType LIKE :mimePrefix ORDER BY f.createdAt DESC")
    List<FileRecord> findByOwnerAndMimeTypeStartingWith(@org.springframework.data.repository.query.Param("owner") User owner, @org.springframework.data.repository.query.Param("mimePrefix") String mimePrefix);

    // Find by storage key
    Optional<FileRecord> findByStorageKey(String storageKey);
}
