package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.FileVersion;
import com.clone.drive.domain.Permission;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FileVersionRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.AuthorizationService;
import com.clone.drive.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/files/{fileId}/versions")
public class VersionController {

    private final FileRecordRepository fileRecordRepository;
    private final FileVersionRepository fileVersionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuthorizationService authService;

    public VersionController(FileRecordRepository fileRecordRepository,
                             FileVersionRepository fileVersionRepository,
                             UserRepository userRepository,
                             StorageService storageService,
                             AuthorizationService authService) {
        this.fileRecordRepository = fileRecordRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.authService = authService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Upload a new version of an existing file.
     */
    @PostMapping
    public ResponseEntity<?> uploadNewVersion(@PathVariable UUID fileId, 
                                              @RequestParam("file") MultipartFile file) {
        try {
            User user = getAuthenticatedUser();
            Optional<FileRecord> fileOpt = fileRecordRepository.findById(fileId);

            if (fileOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
            }
            
            FileRecord fileRecord = fileOpt.get();

            // Requires EDITOR permission to upload a new version
            if (!authService.canAccessFile(user, fileRecord, Permission.PermissionLevel.EDITOR)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to create new versions");
            }

            // Save the CURRENT state as a FileVersion history record
            FileVersion oldVersion = new FileVersion();
            oldVersion.setFileRecord(fileRecord);
            int currentCount = fileVersionRepository.countByFileRecord(fileRecord);
            oldVersion.setVersionNumber(currentCount + 1);
            oldVersion.setSize(fileRecord.getSize());
            oldVersion.setStorageKey(fileRecord.getStorageKey());
            oldVersion.setSha256(fileRecord.getSha256());
            fileVersionRepository.save(oldVersion);

            // Calculate SHA-256 hash for the NEW file
            String sha256Hash;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
                sha256Hash = HexFormat.of().formatHex(digest.digest());
            }

            // Store the new physical file
            UUID newUuid = UUID.randomUUID();
            String newStorageKey = user.getId().toString() + "/" + newUuid.toString() + "_" + file.getOriginalFilename();
            storageService.store(file, newStorageKey);

            // Update the main FileRecord to point to the new data
            fileRecord.setName(file.getOriginalFilename());
            fileRecord.setMimeType(file.getContentType());
            fileRecord.setSize(file.getSize());
            fileRecord.setStorageKey(newStorageKey);
            fileRecord.setSha256(sha256Hash);
            fileRecordRepository.save(fileRecord);

            return ResponseEntity.status(HttpStatus.CREATED).body(fileRecord);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload new version: " + e.getMessage());
        }
    }

    /**
     * List all previous versions of a file.
     */
    @GetMapping
    public ResponseEntity<?> listVersions(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> fileOpt = fileRecordRepository.findById(fileId);

        if (fileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }
        
        FileRecord fileRecord = fileOpt.get();

        // Requires VIEWER permission to list versions
        if (!authService.canAccessFile(user, fileRecord, Permission.PermissionLevel.VIEWER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to view versions");
        }

        List<FileVersion> versions = fileVersionRepository.findByFileRecordOrderByVersionNumberDesc(fileRecord);
        return ResponseEntity.ok(versions);
    }
}
