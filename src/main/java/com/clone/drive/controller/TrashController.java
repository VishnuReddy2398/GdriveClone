package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FolderRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trash/Recycle Bin controller — soft-delete and restore files and folders.
 * Items in trash are automatically purged after 30 days (via a scheduled task).
 */
@RestController
@RequestMapping("/api/trash")
public class TrashController {

    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public TrashController(FileRecordRepository fileRecordRepository,
                           FolderRepository folderRepository,
                           UserRepository userRepository,
                           StorageService storageService) {
        this.fileRecordRepository = fileRecordRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Move a file to trash (soft delete).
     */
    @PostMapping("/file/{fileId}")
    public ResponseEntity<?> trashFile(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(fileId);

        if (recordOpt.isEmpty() || !recordOpt.get().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord record = recordOpt.get();
        record.setDeletedAt(LocalDateTime.now());
        fileRecordRepository.save(record);

        return ResponseEntity.ok(Map.of("message", "File moved to trash"));
    }

    /**
     * Move a folder to trash (soft delete).
     */
    @PostMapping("/folder/{folderId}")
    public ResponseEntity<?> trashFolder(@PathVariable UUID folderId) {
        User user = getAuthenticatedUser();
        Optional<Folder> folderOpt = folderRepository.findById(folderId);

        if (folderOpt.isEmpty() || !folderOpt.get().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Folder not found");
        }

        Folder folder = folderOpt.get();
        folder.setDeletedAt(LocalDateTime.now());
        folderRepository.save(folder);

        return ResponseEntity.ok(Map.of("message", "Folder moved to trash"));
    }

    /**
     * Restore a file from trash.
     */
    @PostMapping("/file/{fileId}/restore")
    public ResponseEntity<?> restoreFile(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(fileId);

        if (recordOpt.isEmpty() || !recordOpt.get().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord record = recordOpt.get();
        record.setDeletedAt(null); // Un-soft-delete
        fileRecordRepository.save(record);

        return ResponseEntity.ok(Map.of("message", "File restored from trash"));
    }

    /**
     * Restore a folder from trash.
     */
    @PostMapping("/folder/{folderId}/restore")
    public ResponseEntity<?> restoreFolder(@PathVariable UUID folderId) {
        User user = getAuthenticatedUser();
        Optional<Folder> folderOpt = folderRepository.findById(folderId);

        if (folderOpt.isEmpty() || !folderOpt.get().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Folder not found");
        }

        Folder folder = folderOpt.get();
        folder.setDeletedAt(null);
        folderRepository.save(folder);

        return ResponseEntity.ok(Map.of("message", "Folder restored from trash"));
    }

    /**
     * List all trashed items (files and folders) for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<?> listTrash() {
        User user = getAuthenticatedUser();

        List<FileRecord> trashedFiles = fileRecordRepository.findByOwnerAndDeletedAtIsNotNull(user);
        List<Folder> trashedFolders = folderRepository.findByOwnerAndDeletedAtIsNotNull(user);

        return ResponseEntity.ok(Map.of(
                "files", trashedFiles,
                "folders", trashedFolders
        ));
    }

    /**
     * Permanently delete a file from trash. Also removes physical file via StorageService.
     */
    @DeleteMapping("/file/{fileId}/permanent")
    public ResponseEntity<?> permanentlyDeleteFile(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(fileId);

        if (recordOpt.isEmpty() || !recordOpt.get().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord record = recordOpt.get();
        if (record.getDeletedAt() == null) {
            return ResponseEntity.badRequest().body("File must be in trash to be permanently deleted");
        }

        // Delete from physical storage
        storageService.delete(record.getStorageKey());
        
        // Delete metadata
        fileRecordRepository.delete(record);

        return ResponseEntity.ok(Map.of("message", "File permanently deleted"));
    }

    /**
     * Empty entire trash for the current user.
     */
    @DeleteMapping("/empty")
    public ResponseEntity<?> emptyTrash() {
        User user = getAuthenticatedUser();
        
        List<FileRecord> trashedFiles = fileRecordRepository.findByOwnerAndDeletedAtIsNotNull(user);
        int fileCount = 0;
        
        for (FileRecord file : trashedFiles) {
            storageService.delete(file.getStorageKey());
            fileRecordRepository.delete(file);
            fileCount++;
        }

        List<Folder> trashedFolders = folderRepository.findByOwnerAndDeletedAtIsNotNull(user);
        int folderCount = trashedFolders.size();
        folderRepository.deleteAll(trashedFolders);

        return ResponseEntity.ok(Map.of(
                "message", "Trash emptied",
                "filesDeleted", fileCount,
                "foldersDeleted", folderCount
        ));
    }
}
