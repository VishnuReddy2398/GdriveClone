package com.clone.drive.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for file storage operations.
 * Allows swapping between local disk storage and Cloudflare R2 seamlessly.
 */
public interface StorageService {

    /**
     * Store a file and return its storage key.
     * @param file The file to store.
     * @param storageKey The path or key to store it under (e.g., user-id/uuid).
     * @return The final storage key.
     */
    String store(MultipartFile file, String storageKey);

    /**
     * Store a file from an InputStream (useful when the stream is already wrapped/encrypted).
     * @param inputStream The input stream containing the file data.
     * @param storageKey The path or key to store it under.
     * @return The final storage key.
     */
    String store(java.io.InputStream inputStream, String storageKey);

    /**
     * Load a file as a Resource (for direct downloading).
     */
    Resource loadAsResource(String storageKey);

    /**
     * Generate a short-lived presigned URL for direct download (if supported).
     * For local storage, this might just return a local API endpoint URL.
     */
    String generateDownloadUrl(String storageKey);
    
    /**
     * Generate a short-lived presigned URL for direct upload (if supported).
     */
    String generateUploadUrl(String storageKey, String contentType);

    /**
     * Delete a file from storage.
     */
    void delete(String storageKey);

    // --- Multipart Upload Methods ---

    /**
     * Start a multipart upload.
     * @return An upload ID for subsequent parts.
     */
    String startMultipartUpload(String storageKey);

    /**
     * Upload a single part.
     * @return The ETag (or local equivalent) of the uploaded part.
     */
    String uploadPart(String storageKey, String uploadId, int partNumber, MultipartFile file);

    /**
     * Complete the multipart upload.
     * @param partEtags A map of Part Number -> ETag.
     */
    void completeMultipartUpload(String storageKey, String uploadId, Map<Integer, String> partEtags);
}
