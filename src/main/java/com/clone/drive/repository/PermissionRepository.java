package com.clone.drive.repository;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.Permission;
import com.clone.drive.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    // Find permission for a specific user on a specific file
    Optional<Permission> findByUserAndFileRecord(User user, FileRecord fileRecord);

    // Find permission for a specific user on a specific folder
    Optional<Permission> findByUserAndFolder(User user, Folder folder);

    // List all permissions on a file (collaborator list)
    List<Permission> findByFileRecord(FileRecord fileRecord);

    // List all permissions on a folder
    List<Permission> findByFolder(Folder folder);

    // "Shared with me" — all files shared to a user
    List<Permission> findByUserAndFileRecordIsNotNull(User user);

    // "Shared with me" — all folders shared to a user
    List<Permission> findByUserAndFolderIsNotNull(User user);

    // Delete all permissions for a file
    void deleteByFileRecord(FileRecord fileRecord);

    // Delete all permissions for a folder
    void deleteByFolder(Folder folder);
}
