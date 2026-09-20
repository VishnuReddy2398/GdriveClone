package com.clone.drive.service;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.Permission;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FolderRepository;
import com.clone.drive.repository.PermissionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Centralized Authorization Service for resource-level Access Control (ACL).
 * Replaces simple "isOwner" checks in controllers with inherited permissions.
 */
@Service
public class AuthorizationService {

    private final PermissionRepository permissionRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;

    public AuthorizationService(PermissionRepository permissionRepository,
                                FileRecordRepository fileRecordRepository,
                                FolderRepository folderRepository) {
        this.permissionRepository = permissionRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.folderRepository = folderRepository;
    }

    /**
     * Determine if a user has a required permission level on a file.
     * Checks direct file permissions first, then walks up the folder hierarchy.
     */
    @org.springframework.cache.annotation.Cacheable(value = "permissions", key = "'file_' + #user.id + '_' + #file.id + '_' + #requiredLevel")
    public boolean canAccessFile(User user, FileRecord file, Permission.PermissionLevel requiredLevel) {
        // Owner implicitly has OWNER level
        if (file.getOwner().getId().equals(user.getId())) {
            return true;
        }

        // Check direct permission on the file
        Optional<Permission> filePerm = permissionRepository.findByUserAndFileRecord(user, file);
        if (filePerm.isPresent() && filePerm.get().getLevel().hasAtLeast(requiredLevel)) {
            return true;
        }

        // Check inherited permissions from parent folders
        return canAccessFolder(user, file.getFolder(), requiredLevel);
    }

    /**
     * Determine if a user has a required permission level on a folder.
     * Walks up the folder tree until root or permission is found.
     */
    @org.springframework.cache.annotation.Cacheable(value = "permissions", key = "'folder_' + #user.id + '_' + (#folder != null ? #folder.id : 'null') + '_' + #requiredLevel")
    public boolean canAccessFolder(User user, Folder folder, Permission.PermissionLevel requiredLevel) {
        if (folder == null) {
            return false;
        }

        // Owner implicitly has OWNER level
        if (folder.getOwner().getId().equals(user.getId())) {
            return true;
        }

        // Check direct permission on this folder
        Optional<Permission> folderPerm = permissionRepository.findByUserAndFolder(user, folder);
        if (folderPerm.isPresent() && folderPerm.get().getLevel().hasAtLeast(requiredLevel)) {
            return true;
        }

        // Recursively check parent folder
        return canAccessFolder(user, folder.getParent(), requiredLevel);
    }
}
