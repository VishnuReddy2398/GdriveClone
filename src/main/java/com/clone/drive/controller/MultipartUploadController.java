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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files/multipart")
public class MultipartUploadController {

    private final StorageService storageService;
    private final FileRecordRepository fileRecordRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public MultipartUploadController(StorageService storageService, 
                                     FileRecordRepository fileRecordRepository, 
                                     FolderRepository folderRepository, 
                                     UserRepository userRepository) {
        this.storageService = storageService;
        this.fileRecordRepository = fileRecordRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    @PostMapping("/start")
    public ResponseEntity<?> startUpload(@RequestParam String filename) {
        try {
            User user = getAuthenticatedUser();
            UUID fileUuid = UUID.randomUUID();
            String storageKey = user.getId().toString() + "/" + fileUuid.toString() + "_" + filename;
            
            String uploadId = storageService.startMultipartUpload(storageKey);
            
            return ResponseEntity.ok(Map.of(
                    "uploadId", uploadId,
                    "storageKey", storageKey,
                    "fileUuid", fileUuid
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to start upload: " + e.getMessage());
        }
    }

    @PostMapping("/{uploadId}/part")
    public ResponseEntity<?> uploadPart(@PathVariable String uploadId,
                                        @RequestParam String storageKey,
                                        @RequestParam int partNumber,
                                        @RequestParam("file") MultipartFile file) {
        try {
            User user = getAuthenticatedUser();
            String expectedPrefix = user.getId().toString() + "/";
            if (!storageKey.startsWith(expectedPrefix)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized storage key");
            }

            String eTag = storageService.uploadPart(storageKey, uploadId, partNumber, file);
            return ResponseEntity.ok(Map.of("eTag", eTag, "partNumber", partNumber));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload part: " + e.getMessage());
        }
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<?> completeUpload(@PathVariable String uploadId,
                                            @RequestParam String storageKey,
                                            @RequestParam UUID fileUuid,
                                            @RequestParam String filename,
                                            @RequestParam String mimeType,
                                            @RequestParam Long size,
                                            @RequestParam(required = false) UUID folderId,
                                            @RequestBody Map<Integer, String> partEtags) {
        try {
            User user = getAuthenticatedUser();
            String expectedPrefix = user.getId().toString() + "/";
            if (!storageKey.startsWith(expectedPrefix)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized storage key");
            }

            Folder parentFolder = null;

            if (folderId != null) {
                parentFolder = folderRepository.findById(folderId).orElse(null);
                if (parentFolder != null && !parentFolder.getOwner().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied to target folder");
                }
            }

            // Tell storage service to stitch the parts together
            storageService.completeMultipartUpload(storageKey, uploadId, partEtags);

            // Create metadata record
            FileRecord record = new FileRecord();
            record.setId(fileUuid);
            record.setName(filename);
            record.setMimeType(mimeType);
            record.setSize(size);
            record.setOwner(user);
            record.setFolder(parentFolder);
            record.setStorageKey(storageKey);
            
            // Note: In a real system, you might compute the combined SHA-256 after completion
            // For now, we omit it for multipart to simplify.
            
            fileRecordRepository.save(record);

            return ResponseEntity.status(HttpStatus.CREATED).body(record);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to complete upload: " + e.getMessage());
        }
    }
}
