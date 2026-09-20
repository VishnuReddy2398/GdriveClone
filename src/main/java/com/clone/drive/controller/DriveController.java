package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FolderRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.ActivityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Controller for Google-Drive-like features:
 * Star/Favorite, Recent, Rename, Move, Filter, Storage Quota, Activity Log.
 */
@RestController
@RequestMapping("/api/drive")
public class DriveController {

    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public DriveController(FileRecordRepository fileRecordRepository,
                           FolderRepository folderRepository,
                           UserRepository userRepository,
                           ActivityService activityService) {
        this.fileRecordRepository = fileRecordRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    // ==================== STAR / FAVORITE ====================

    @PostMapping("/star/{fileId}")
    public ResponseEntity<?> starFile(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> opt = fileRecordRepository.findById(fileId);
        if (opt.isEmpty() || !opt.get().getOwner().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        FileRecord r = opt.get();
        r.setStarred(true);
        fileRecordRepository.save(r);
        activityService.log(user, "STAR", "Starred " + r.getName(), fileId, null);
        return ResponseEntity.ok(Map.of("message", "File starred"));
    }

    @PostMapping("/unstar/{fileId}")
    public ResponseEntity<?> unstarFile(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> opt = fileRecordRepository.findById(fileId);
        if (opt.isEmpty() || !opt.get().getOwner().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        FileRecord r = opt.get();
        r.setStarred(false);
        fileRecordRepository.save(r);
        activityService.log(user, "UNSTAR", "Unstarred " + r.getName(), fileId, null);
        return ResponseEntity.ok(Map.of("message", "File unstarred"));
    }

    @GetMapping("/starred")
    public ResponseEntity<?> getStarredFiles() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(fileRecordRepository.findByOwnerAndStarredTrueAndDeletedAtIsNull(user));
    }

    // ==================== RECENT FILES ====================

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentFiles() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(fileRecordRepository.findTop20ByOwnerAndDeletedAtIsNullOrderByLastAccessedAtDesc(user));
    }

    // ==================== RENAME ====================

    @PutMapping("/rename/file/{fileId}")
    public ResponseEntity<?> renameFile(@PathVariable UUID fileId, @RequestParam String newName) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> opt = fileRecordRepository.findById(fileId);
        if (opt.isEmpty() || !opt.get().getOwner().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        FileRecord r = opt.get();
        String oldName = r.getName();
        r.setName(newName);
        fileRecordRepository.save(r);
        activityService.log(user, "RENAME", "Renamed " + oldName + " to " + newName, fileId, null);
        return ResponseEntity.ok(r);
    }

    @PutMapping("/rename/folder/{folderId}")
    public ResponseEntity<?> renameFolder(@PathVariable UUID folderId, @RequestParam String newName) {
        User user = getAuthenticatedUser();
        Optional<Folder> opt = folderRepository.findById(folderId);
        if (opt.isEmpty() || !opt.get().getOwner().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Folder not found");
        Folder f = opt.get();
        f.setName(newName);
        folderRepository.save(f);
        activityService.log(user, "RENAME", "Renamed folder to " + newName, null, folderId);
        return ResponseEntity.ok(f);
    }

    // ==================== MOVE ====================

    @PutMapping("/move/file/{fileId}")
    public ResponseEntity<?> moveFile(@PathVariable UUID fileId, @RequestParam(required = false) UUID targetFolderId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> opt = fileRecordRepository.findById(fileId);
        if (opt.isEmpty() || !opt.get().getOwner().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        FileRecord r = opt.get();
        if (targetFolderId != null) {
            Folder target = folderRepository.findById(targetFolderId).orElse(null);
            if (target == null || !target.getOwner().getId().equals(user.getId()))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Target folder not found");
            r.setFolder(target);
        } else {
            r.setFolder(null); // Move to root
        }
        fileRecordRepository.save(r);
        activityService.log(user, "MOVE", "Moved " + r.getName(), fileId, targetFolderId);
        return ResponseEntity.ok(r);
    }

    @PutMapping("/move/folder/{folderId}")
    public ResponseEntity<?> moveFolder(@PathVariable UUID folderId, @RequestParam(required = false) UUID targetFolderId) {
        User user = getAuthenticatedUser();
        Optional<Folder> opt = folderRepository.findById(folderId);
        if (opt.isEmpty() || !opt.get().getOwner().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Folder not found");
        Folder f = opt.get();
        if (targetFolderId != null) {
            if (targetFolderId.equals(folderId))
                return ResponseEntity.badRequest().body("Cannot move folder into itself");
            Folder target = folderRepository.findById(targetFolderId).orElse(null);
            if (target == null || !target.getOwner().getId().equals(user.getId()))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Target folder not found");
            f.setParent(target);
        } else {
            f.setParent(null); // Move to root
        }
        folderRepository.save(f);
        activityService.log(user, "MOVE", "Moved folder " + f.getName(), null, folderId);
        return ResponseEntity.ok(f);
    }

    // ==================== BULK OPERATIONS ====================

    @PostMapping("/bulk/delete")
    public ResponseEntity<?> bulkDelete(@RequestBody Map<String, List<UUID>> body) {
        User user = getAuthenticatedUser();
        List<UUID> fileIds = body.getOrDefault("fileIds", Collections.emptyList());
        List<UUID> folderIds = body.getOrDefault("folderIds", Collections.emptyList());
        int count = 0;
        for (UUID id : fileIds) {
            Optional<FileRecord> opt = fileRecordRepository.findById(id);
            if (opt.isPresent() && opt.get().getOwner().getId().equals(user.getId())) {
                opt.get().setDeletedAt(LocalDateTime.now());
                fileRecordRepository.save(opt.get());
                count++;
            }
        }
        for (UUID id : folderIds) {
            Optional<Folder> opt = folderRepository.findById(id);
            if (opt.isPresent() && opt.get().getOwner().getId().equals(user.getId())) {
                opt.get().setDeletedAt(LocalDateTime.now());
                folderRepository.save(opt.get());
                count++;
            }
        }
        activityService.log(user, "BULK_DELETE", "Bulk deleted " + count + " items", null, null);
        return ResponseEntity.ok(Map.of("deleted", count));
    }

    @PostMapping("/bulk/move")
    public ResponseEntity<?> bulkMove(@RequestBody Map<String, Object> body) {
        User user = getAuthenticatedUser();
        @SuppressWarnings("unchecked")
        List<String> fileIds = (List<String>) body.getOrDefault("fileIds", Collections.emptyList());
        UUID targetFolderId = body.get("targetFolderId") != null ? UUID.fromString(body.get("targetFolderId").toString()) : null;
        Folder target = null;
        if (targetFolderId != null) {
            target = folderRepository.findById(targetFolderId).orElse(null);
            if (target == null || !target.getOwner().getId().equals(user.getId()))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Target folder not found");
        }
        int count = 0;
        for (String idStr : fileIds) {
            UUID id = UUID.fromString(idStr);
            Optional<FileRecord> opt = fileRecordRepository.findById(id);
            if (opt.isPresent() && opt.get().getOwner().getId().equals(user.getId())) {
                opt.get().setFolder(target);
                fileRecordRepository.save(opt.get());
                count++;
            }
        }
        return ResponseEntity.ok(Map.of("moved", count));
    }

    // ==================== FILTER BY TYPE ====================

    @GetMapping("/filter")
    public ResponseEntity<?> filterByType(@RequestParam String type) {
        User user = getAuthenticatedUser();
        String mimePrefix = switch (type.toLowerCase()) {
            case "images" -> "image/%";
            case "videos" -> "video/%";
            case "audio" -> "audio/%";
            case "documents" -> "application/pdf";
            default -> type + "/%";
        };
        return ResponseEntity.ok(fileRecordRepository.findByOwnerAndMimeTypeStartingWith(user, mimePrefix));
    }

    // ==================== STORAGE QUOTA ====================

    @GetMapping("/quota")
    public ResponseEntity<?> getStorageQuota() {
        User user = getAuthenticatedUser();
        List<FileRecord> allFiles = fileRecordRepository.findByOwnerAndFolderIsNullAndDeletedAtIsNull(user);
        // This is a simplified approach - in production you'd use a SUM query
        long totalUsed = fileRecordRepository.findAll().stream()
                .filter(f -> f.getOwner().getId().equals(user.getId()) && f.getDeletedAt() == null)
                .mapToLong(f -> f.getSize() != null ? f.getSize() : 0)
                .sum();
        long quotaBytes = 10L * 1024 * 1024 * 1024; // 10 GB (R2 free tier)
        return ResponseEntity.ok(Map.of(
                "usedBytes", totalUsed,
                "usedMB", totalUsed / (1024 * 1024),
                "quotaBytes", quotaBytes,
                "quotaMB", quotaBytes / (1024 * 1024),
                "percentUsed", (double) totalUsed / quotaBytes * 100
        ));
    }

    // ==================== ACTIVITY LOG ====================

    @GetMapping("/activity")
    public ResponseEntity<?> getActivity() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(activityService.getRecentActivity(user));
    }
}
