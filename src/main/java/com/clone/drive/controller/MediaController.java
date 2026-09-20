package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

import com.clone.drive.domain.Permission;
import com.clone.drive.service.AuthorizationService;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;

/**
 * Controller for streaming/previewing media files (images, video, audio).
 * Supports inline content disposition so browsers can render previews
 * instead of forcing downloads — just like Google Drive.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuthorizationService authService;

    public MediaController(FileRecordRepository fileRecordRepository,
                           UserRepository userRepository,
                           StorageService storageService,
                           AuthorizationService authService) {
        this.fileRecordRepository = fileRecordRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.authService = authService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Preview/stream a media file inline in the browser.
     * Only strictly verified safe formats are rendered inline.
     * Dangerous formats (HTML, SVG, XML, scripts) are blocked from inline execution.
     */
    @GetMapping("/preview/{fileId}")
    public ResponseEntity<?> previewMedia(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(fileId);

        if (recordOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord record = recordOpt.get();
        if (record.getDeletedAt() != null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        // Authorization check via ACL
        if (!authService.canAccessFile(user, record, Permission.PermissionLevel.VIEWER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied to file");
        }

        String mimeType = record.getMimeType();

        // Strictly verify that this file type can be displayed inline
        if (!isPreviewable(mimeType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("This file type cannot be previewed inline for security reasons. Use the download endpoint instead.");
        }

        try {
            Resource resource = storageService.loadAsResource(record.getStorageKey());
            String disposition = ContentDisposition.inline()
                    .filename(record.getName(), StandardCharsets.UTF_8)
                    .build()
                    .toString();
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(resource);
        } catch (UnsupportedOperationException e) {
            // R2 mode: redirect to presigned URL instead of streaming through server
            String presignedUrl = storageService.generateDownloadUrl(record.getStorageKey());
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .header(HttpHeaders.LOCATION, presignedUrl)
                    .build();
        }
    }

    /**
     * Get metadata about a file including its preview capability.
     * Frontend uses this to decide what UI to render (image viewer, video player, etc.)
     */
    @GetMapping("/info/{fileId}")
    public ResponseEntity<?> getMediaInfo(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(fileId);

        if (recordOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord record = recordOpt.get();
        if (record.getDeletedAt() != null || !authService.canAccessFile(user, record, Permission.PermissionLevel.VIEWER)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found or access denied");
        }

        String mimeType = record.getMimeType() != null ? record.getMimeType() : "application/octet-stream";

        return ResponseEntity.ok(java.util.Map.of(
                "id", record.getId(),
                "name", record.getName(),
                "mimeType", mimeType,
                "size", record.getSize(),
                "category", getFileCategory(mimeType),
                "previewable", isPreviewable(mimeType),
                "previewUrl", "/api/media/preview/" + record.getId(),
                "downloadUrl", "/api/files/" + record.getId() + "/download-url"
        ));
    }

    /**
     * Check if a MIME type is strictly safe for inline browser preview.
     * Disallows arbitrary text/*, HTML, SVG, XML to prevent Stored XSS.
     */
    private boolean isPreviewable(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase().trim();

        // Explicit blacklist of scriptable/dangerous web content types
        if (lower.equals("image/svg+xml")
                || lower.equals("text/html")
                || lower.equals("application/xhtml+xml")
                || lower.equals("text/xml")
                || lower.equals("application/xml")
                || lower.startsWith("text/")) {
            return false;
        }

        // Strict allowlist for raster images
        if (lower.equals("image/jpeg")
                || lower.equals("image/png")
                || lower.equals("image/webp")
                || lower.equals("image/gif")) {
            return true;
        }

        // PDF and streaming media (video/audio)
        return lower.equals("application/pdf")
                || lower.startsWith("video/")
                || lower.startsWith("audio/");
    }

    /**
     * Categorize a file by its MIME type — similar to Google Drive's file type grouping.
     */
    private String getFileCategory(String mimeType) {
        if (mimeType == null) return "OTHER";
        String lower = mimeType.toLowerCase();
        if (lower.startsWith("image/")) return "IMAGE";
        if (lower.startsWith("video/")) return "VIDEO";
        if (lower.startsWith("audio/")) return "AUDIO";
        if (lower.equals("application/pdf")) return "PDF";
        if (lower.startsWith("text/")) return "TEXT";
        if (lower.contains("spreadsheet") || lower.contains("excel")) return "SPREADSHEET";
        if (lower.contains("document") || lower.contains("word")) return "DOCUMENT";
        if (lower.contains("presentation") || lower.contains("powerpoint")) return "PRESENTATION";
        if (lower.contains("zip") || lower.contains("rar") || lower.contains("tar") || lower.contains("gzip")) return "ARCHIVE";
        return "OTHER";
    }
}
