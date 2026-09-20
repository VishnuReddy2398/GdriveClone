package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Folder;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.FolderRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.StorageService;
import com.clone.drive.service.AuthorizationService;
import com.clone.drive.domain.Permission;
import com.clone.drive.event.FileEventPublisher;
import com.clone.drive.event.FileUploadedEvent;
import com.clone.drive.service.EncryptionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.apache.tika.Tika;

import com.clone.drive.domain.FileVersion;
import com.clone.drive.repository.FileVersionRepository;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final Tika tika = new Tika();

    private final FileRecordRepository fileRecordRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final FileEventPublisher eventPublisher;
    private final AuthorizationService authService;
    private final EncryptionService encryptionService;

    public FileController(FileRecordRepository fileRecordRepository, 
                          FileVersionRepository fileVersionRepository,
                          FolderRepository folderRepository, 
                          UserRepository userRepository, 
                          StorageService storageService,
                          FileEventPublisher eventPublisher,
                          AuthorizationService authService,
                          EncryptionService encryptionService) {
        this.fileRecordRepository = fileRecordRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
        this.authService = authService;
        this.encryptionService = encryptionService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    @GetMapping("/upload-url")
    public ResponseEntity<?> getUploadUrl(@RequestParam String filename,
                                          @RequestParam String contentType,
                                          @RequestParam(required = false) UUID folderId) {
        User user = getAuthenticatedUser();
        Folder parentFolder = null;

        if (folderId != null) {
            parentFolder = folderRepository.findById(folderId).orElse(null);
            if (parentFolder != null && !authService.canAccessFolder(user, parentFolder, Permission.PermissionLevel.EDITOR)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied to target folder");
            }
        }

        UUID fileUuid = UUID.randomUUID();
        String storageKey = user.getId().toString() + "/" + fileUuid.toString() + "_" + filename;
        String uploadUrl = storageService.generateUploadUrl(storageKey, contentType);

        return ResponseEntity.ok(java.util.Map.of(
            "uploadUrl", uploadUrl,
            "storageKey", storageKey
        ));
    }

    @PostMapping("/finalize-upload")
    public ResponseEntity<?> finalizeUpload(@RequestBody java.util.Map<String, Object> payload) {
        try {
            User user = getAuthenticatedUser();
            String storageKey = (String) payload.get("storageKey");
            String filename = (String) payload.get("filename");
            String contentType = (String) payload.get("contentType");
            Number sizeNum = (Number) payload.get("size");
            Long size = sizeNum != null ? sizeNum.longValue() : 0L;
            String folderIdStr = (String) payload.get("folderId");
            UUID folderId = folderIdStr != null ? UUID.fromString(folderIdStr) : null;
            String sha256Hash = (String) payload.get("sha256");

            Folder parentFolder = null;
            if (folderId != null) {
                parentFolder = folderRepository.findById(folderId).orElse(null);
            }

            FileRecord record = new FileRecord();
            record.setName(filename);
            record.setMimeType(contentType);
            record.setSize(size);
            record.setOwner(user);
            record.setFolder(parentFolder);
            record.setStorageKey(storageKey);
            record.setSha256(sha256Hash); 
            // We no longer have wrappedDek or iv because we abandoned Envelope Encryption for direct uploads
            
            fileRecordRepository.save(record);

            eventPublisher.publishFileUploaded(new FileUploadedEvent(
                    record.getId(), storageKey, contentType,
                    filename, user.getId()
            ));

            return ResponseEntity.status(HttpStatus.CREATED).body(record);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to finalize upload: " + e.getMessage());
        }
    }

    /**
     * Get a download URL. 
     * For R2, this returns a presigned URL. For local, it returns a local API endpoint URL.
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<?> getDownloadUrl(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(id);

        if (recordOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }
        
        FileRecord file = recordOpt.get();
        // Use ACL for file check (requires VIEWER)
        if (!authService.canAccessFile(user, file, Permission.PermissionLevel.VIEWER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied to file");
        }

        String url = storageService.generateDownloadUrl(file.getStorageKey());
        return ResponseEntity.ok(url);
    }

    /**
     * Directly download the file contents (checking permissions).
     * Used by the frontend for file previews and direct downloads.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFileDirect(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(id);

        if (recordOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        FileRecord file = recordOpt.get();
        if (!authService.canAccessFile(user, file, Permission.PermissionLevel.VIEWER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Resource resource = storageService.loadAsResource(file.getStorageKey());
            Resource body = resource;
            
            if (file.getWrappedDek() != null && file.getIv() != null) {
                SecretKey dek = encryptionService.unwrapDek(file.getWrappedDek());
                byte[] iv = Base64.getDecoder().decode(file.getIv());
                InputStream is = resource.getInputStream();
                InputStream decryptedStream = encryptionService.getDecryptingInputStream(is, dek, iv);
                body = new InputStreamResource(decryptedStream);
            }

            String contentDisposition = ContentDisposition.attachment()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build()
                    .toString();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(MediaType.parseMediaType(file.getMimeType() != null ? file.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint specifically used when storage.type=local.
     * Serves the actual bytes from the local disk with strict ACL validation.
     */
    @GetMapping("/download/local/{userId}/{filename}")
    public ResponseEntity<Resource> downloadLocalFile(@PathVariable String userId, @PathVariable String filename) {
        String storageKey = userId + "/" + filename;
        User user = getAuthenticatedUser();

        // 1. Locate FileRecord by storageKey (or via FileVersion)
        Optional<FileRecord> recordOpt = fileRecordRepository.findByStorageKey(storageKey);
        FileRecord file = null;
        if (recordOpt.isPresent()) {
            file = recordOpt.get();
        } else {
            Optional<FileVersion> versionOpt = fileVersionRepository.findByStorageKey(storageKey);
            if (versionOpt.isPresent()) {
                file = versionOpt.get().getFileRecord();
            }
        }

        if (file == null || file.getDeletedAt() != null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 2. Authorization check against ACL
        if (!authService.canAccessFile(user, file, Permission.PermissionLevel.VIEWER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Resource resource = storageService.loadAsResource(storageKey);
            String contentDisposition = ContentDisposition.attachment()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build()
                    .toString();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(MediaType.parseMediaType(file.getMimeType() != null ? file.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Endpoint specifically used when storage.type=local to simulate a presigned upload.
     * Validates that authenticated user can only upload to their own storage location.
     */
    @PutMapping("/upload/local/{userId}/{filename}")
    public ResponseEntity<?> uploadLocalFile(@PathVariable String userId, @PathVariable String filename, InputStream inputStream) {
        User user = getAuthenticatedUser();
        String expectedPrefix = user.getId().toString() + "/";
        String storageKey = userId + "/" + filename;

        if (!storageKey.startsWith(expectedPrefix)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot upload to another user's storage");
        }

        storageService.store(inputStream, storageKey);
        return ResponseEntity.ok().build();
    }

    /**
     * Soft delete a file by moving it to the trash.
     */
    @PostMapping("/{id}/trash")
    public ResponseEntity<?> trashFile(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> recordOpt = fileRecordRepository.findById(id);

        if (recordOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord file = recordOpt.get();
        if (!file.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }

        file.setDeletedAt(java.time.LocalDateTime.now());
        fileRecordRepository.save(file);

        return ResponseEntity.ok().build();
    }
}
