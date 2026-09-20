package com.clone.drive.controller;

import com.clone.drive.domain.Folder;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FolderRepository;
import com.clone.drive.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;

    public FolderController(FolderRepository folderRepository, 
                            FileRecordRepository fileRecordRepository, 
                            UserRepository userRepository) {
        this.folderRepository = folderRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // The principal is the UserDetails object we loaded in CustomUserDetailsService
        String email = auth.getName(); 
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB"));
    }

    /**
     * Create a new folder.
     */
    @PostMapping
    public ResponseEntity<?> createFolder(@RequestParam String name, 
                                          @RequestParam(required = false) UUID parentId) {
        User user = getAuthenticatedUser();
        
        Folder folder = new Folder();
        folder.setName(name);
        folder.setOwner(user);
        
        if (parentId != null) {
            Optional<Folder> parentOpt = folderRepository.findById(parentId);
            if (parentOpt.isPresent()) {
                Folder parent = parentOpt.get();
                // Security check: ensure the parent folder actually belongs to the user
                if (!parent.getOwner().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied to parent folder");
                }
                folder.setParent(parent);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parent folder not found");
            }
        }
        
        Folder savedFolder = folderRepository.save(folder);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedFolder);
    }

    /**
     * List contents of a folder (or root if id is not provided).
     */
    @GetMapping({"", "/{id}"})
    public ResponseEntity<?> listContents(@PathVariable(required = false) UUID id) {
        User user = getAuthenticatedUser();
        Map<String, Object> response = new HashMap<>();
        
        if (id == null) {
            // List Root contents
            response.put("folders", folderRepository.findByOwnerAndParentIsNullAndDeletedAtIsNull(user));
            response.put("files", fileRecordRepository.findByOwnerAndFolderIsNullAndDeletedAtIsNull(user));
        } else {
            // List specific folder contents
            Optional<Folder> folderOpt = folderRepository.findById(id);
            if (folderOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Folder not found");
            }
            
            Folder targetFolder = folderOpt.get();
            // Security check
            if (!targetFolder.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
            }
            
            response.put("currentFolder", targetFolder);
            response.put("folders", folderRepository.findByOwnerAndParentAndDeletedAtIsNull(user, targetFolder));
            response.put("files", fileRecordRepository.findByOwnerAndFolderAndDeletedAtIsNull(user, targetFolder));
            
            // Build the breadcrumb path
            java.util.List<Map<String, String>> path = new java.util.ArrayList<>();
            Folder current = targetFolder;
            while (current != null) {
                Map<String, String> pathNode = new HashMap<>();
                pathNode.put("id", current.getId().toString());
                pathNode.put("name", current.getName());
                path.add(0, pathNode); // Add at the beginning to maintain root -> current order
                current = current.getParent();
            }
            response.put("path", path);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Soft delete a folder by moving it to the trash.
     */
    @PostMapping("/{id}/trash")
    public ResponseEntity<?> trashFolder(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<Folder> folderOpt = folderRepository.findById(id);

        if (folderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Folder not found");
        }

        Folder folder = folderOpt.get();
        if (!folder.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }

        folder.setDeletedAt(java.time.LocalDateTime.now());
        folderRepository.save(folder);

        return ResponseEntity.ok().build();
    }
}
