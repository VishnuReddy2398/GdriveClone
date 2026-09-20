package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.Permission;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FolderRepository;
import com.clone.drive.repository.PermissionRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionRepository permissionRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authService;

    public PermissionController(PermissionRepository permissionRepository,
                                FileRecordRepository fileRecordRepository,
                                FolderRepository folderRepository,
                                UserRepository userRepository,
                                AuthorizationService authService) {
        this.permissionRepository = permissionRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Share a file with another user (by email).
     */
    @PostMapping("/file/{fileId}")
    public ResponseEntity<?> shareFile(@PathVariable UUID fileId, 
                                       @RequestParam String targetEmail, 
                                       @RequestParam String level) {
        User user = getAuthenticatedUser();
        
        Optional<FileRecord> fileOpt = fileRecordRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        FileRecord file = fileOpt.get();

        // Must be OWNER to grant permissions
        if (!authService.canAccessFile(user, file, Permission.PermissionLevel.OWNER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to share this file");
        }

        Optional<User> targetUserOpt = userRepository.findByEmail(targetEmail);
        if (targetUserOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Target user not found");
        }
        User targetUser = targetUserOpt.get();

        if (targetUser.getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body("Cannot share with yourself");
        }

        Permission.PermissionLevel permLevel;
        try {
            permLevel = Permission.PermissionLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid permission level");
        }

        // Upsert permission
        Permission permission = permissionRepository.findByUserAndFileRecord(targetUser, file)
                .orElse(new Permission());
        
        permission.setUser(targetUser);
        permission.setFileRecord(file);
        permission.setLevel(permLevel);
        permission.setGrantedBy(user);
        
        permissionRepository.save(permission);
        
        return ResponseEntity.ok(Map.of("message", "File shared successfully"));
    }

    /**
     * Get files shared with me
     */
    @GetMapping("/shared-with-me")
    public ResponseEntity<?> getSharedWithMe() {
        User user = getAuthenticatedUser();
        // Return files where the user has explicit permissions (or inherited, but simplified here)
        return ResponseEntity.ok(permissionRepository.findByUserAndFileRecordIsNotNull(user));
    }
}
