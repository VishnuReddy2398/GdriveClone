package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Handles bulk download of multiple files as a single ZIP archive.
 * Streams the zip directly to the HTTP response to avoid buffering in memory.
 */
@RestController
@RequestMapping("/api/download")
public class ZipDownloadController {

    private static final Logger log = LoggerFactory.getLogger(ZipDownloadController.class);

    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public ZipDownloadController(FileRecordRepository fileRecordRepository,
                                 UserRepository userRepository,
                                 StorageService storageService) {
        this.fileRecordRepository = fileRecordRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * POST /api/download/zip
     * Body: list of file UUIDs
     * Streams a ZIP file containing all requested files.
     */
    @PostMapping("/zip")
    public void downloadAsZip(@RequestBody List<UUID> fileIds, HttpServletResponse response) {
        User user = getAuthenticatedUser();

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"drive-download.zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (UUID fileId : fileIds) {
                FileRecord file = fileRecordRepository.findById(fileId).orElse(null);
                if (file == null) {
                    log.warn("File not found, skipping: {}", fileId);
                    continue;
                }

                // Only allow downloading files the user owns
                if (!file.getOwner().getId().equals(user.getId())) {
                    log.warn("User {} does not own file {}, skipping", user.getEmail(), fileId);
                    continue;
                }

                try {
                    Resource resource = storageService.loadAsResource(file.getStorageKey());
                    InputStream inputStream = resource.getInputStream();

                    ZipEntry entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);
                    inputStream.transferTo(zos);
                    zos.closeEntry();
                    inputStream.close();

                    log.info("Added to zip: {}", file.getName());
                } catch (UnsupportedOperationException e) {
                    log.info("Skipping file in R2 mode (streaming not supported): {}", file.getName());
                } catch (Exception e) {
                    log.error("Error adding file to zip: {}", file.getName(), e);
                }
            }

            zos.finish();
        } catch (Exception e) {
            log.error("Failed to create zip download", e);
        }
    }
}
