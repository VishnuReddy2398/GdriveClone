package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.ShareLink;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FolderRepository;
import com.clone.drive.repository.ShareLinkRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareLinkRepository shareLinkRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public ShareController(ShareLinkRepository shareLinkRepository,
                           FileRecordRepository fileRecordRepository,
                           FolderRepository folderRepository,
                           UserRepository userRepository,
                           StorageService storageService) {
        this.shareLinkRepository = shareLinkRepository;
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
     * Generate a share link for a specific file.
     * Requires Authentication.
     */
    @PostMapping("/file/{fileId}")
    public ResponseEntity<?> createShareLink(@PathVariable UUID fileId, 
                                             @RequestParam(required = false) Long expiryHours) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> fileOpt = fileRecordRepository.findById(fileId);

        if (fileOpt.isEmpty() || !fileOpt.get().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found or access denied");
        }

        ShareLink link = new ShareLink();
        // Generate a 32-character secure random token
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setFileRecord(fileOpt.get());
        
        if (expiryHours != null) {
            link.setExpiresAt(LocalDateTime.now().plusHours(expiryHours));
        }

        shareLinkRepository.save(link);

        return ResponseEntity.ok(Map.of("token", link.getToken(), "expiresAt", link.getExpiresAt()));
    }

    /**
     * Public endpoint to access a shared file via token.
     * Does NOT require Authentication (must be permitted in SecurityConfig).
     */
    @GetMapping("/access/{token}")
    public ResponseEntity<?> accessSharedFile(@PathVariable String token) {
        Optional<ShareLink> linkOpt = shareLinkRepository.findByToken(token);

        if (linkOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Link invalid or not found");
        }

        ShareLink link = linkOpt.get();

        // Check expiry
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.GONE).body("This share link has expired");
        }

        if (link.getFileRecord() != null) {
            // Generate a download URL directly from storage service (presigned URL for R2)
            String downloadUrl = storageService.generateDownloadUrl(link.getFileRecord().getStorageKey());
            return ResponseEntity.ok(Map.of(
                    "type", "FILE",
                    "filename", link.getFileRecord().getName(),
                    "mimeType", link.getFileRecord().getMimeType(),
                    "downloadUrl", downloadUrl
            ));
        } else if (link.getFolder() != null) {
            return ResponseEntity.ok(Map.of(
                    "type", "FOLDER",
                    "folderName", link.getFolder().getName(),
                    "message", "Folder listing for shares not fully implemented yet"
            ));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Invalid share link state");
    }
}
