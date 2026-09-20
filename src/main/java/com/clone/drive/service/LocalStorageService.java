package com.clone.drive.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Local disk implementation of StorageService.
 * Activated when storage.type=local in application.properties.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private final Path rootLocation;

    public LocalStorageService(@Value("${storage.local.dir:./data/storage}") String storageDir) {
        this.rootLocation = Paths.get(storageDir);
        init();
    }

    private void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Initialized local storage at {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local storage directory", e);
        }
    }

    private Path resolveSecurePath(String storageKey) {
        Path root = this.rootLocation.toAbsolutePath().normalize();
        Path destination = root.resolve(Paths.get(storageKey)).normalize();

        if (!destination.startsWith(root)) {
            throw new SecurityException("Invalid storage path");
        }

        return destination;
    }

    @Override
    public String store(MultipartFile file, String storageKey) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }
            return store(file.getInputStream(), storageKey);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file.", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String storageKey) {
        try {
            Path destinationFile = resolveSecurePath(storageKey);
            Files.createDirectories(destinationFile.getParent());
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return storageKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file locally.", e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        try {
            Path file = resolveSecurePath(storageKey);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not read file", e);
        }
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        // For local dev, we route downloads through a specific controller endpoint
        return "/api/files/download/local/" + storageKey;
    }

    @Override
    public String generateUploadUrl(String storageKey, String contentType) {
        // For local dev, we route uploads through a specific controller endpoint
        return "/api/files/upload/local/" + storageKey;
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path file = resolveSecurePath(storageKey);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete local file", e);
        }
    }

    @Override
    public String startMultipartUpload(String storageKey) {
        // Return a dummy upload ID for local mode
        return "local-upload-" + System.currentTimeMillis();
    }

    @Override
    public String uploadPart(String storageKey, String uploadId, int partNumber, MultipartFile file) {
        try {
            Path destinationFile = resolveSecurePath(storageKey);
            Files.createDirectories(destinationFile.getParent());
            Files.write(destinationFile, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "local-etag-" + partNumber;
        } catch (IOException e) {
            throw new RuntimeException("Failed to append local part", e);
        }
    }

    @Override
    public void completeMultipartUpload(String storageKey, String uploadId, Map<Integer, String> partEtags) {
        log.info("Completed local multipart upload for {}", storageKey);
    }
}
