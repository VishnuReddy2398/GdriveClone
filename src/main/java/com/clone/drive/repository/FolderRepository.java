package com.clone.drive.repository;

import com.clone.drive.domain.Folder;
import com.clone.drive.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {
    
    // Find subfolders for a specific parent folder belonging to a user
    List<Folder> findByOwnerAndParentAndDeletedAtIsNull(User owner, Folder parent);
    
    // Find root folders (parent is null) belonging to a user
    List<Folder> findByOwnerAndParentIsNullAndDeletedAtIsNull(User owner);
    
    // Find trashed folders belonging to a user
    List<Folder> findByOwnerAndDeletedAtIsNotNull(User owner);
}
