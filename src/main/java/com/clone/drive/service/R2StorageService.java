package com.clone.drive.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cloudflare R2 implementation of StorageService using AWS SDK.
 * Activated when storage.type=r2 in application.properties.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "r2")
public class R2StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);
    
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName = "personal-drive-storage";

    public R2StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    @CircuitBreaker(name = "r2Storage", fallbackMethod = "storeFallback")
    public String store(MultipartFile file, String storageKey) {
        try {
            return store(file.getInputStream(), storageKey);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file for R2 upload", e);
        }
    }

    @Override
    public String store(java.io.InputStream inputStream, String storageKey) {
        try {
            log.info("Uploading stream to R2: {}", storageKey);
            // We use available() as an estimate, this is problematic for large streams in S3.
            // Ideally we should use a temporary file or chunked upload.
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .build(),
                    RequestBody.fromInputStream(inputStream, inputStream.available()));
            return storageKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload to R2", e);
        }
    }

    public String storeFallback(MultipartFile file, String storageKey, Throwable t) {
        throw new RuntimeException("R2 Storage is currently unavailable. Circuit breaker is open. Underlying cause: " + t.getMessage());
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        // For R2, we typically prefer presigned URLs rather than streaming through our server.
        // If we really need the InputStream, we can fetch it, but usually generateDownloadUrl is better.
        throw new UnsupportedOperationException("Use generateDownloadUrl() for R2 instead of loadAsResource()");
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60)) // URL valid for 1 hour
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        return presignedGetObjectRequest.url().toString();
    }

    @Override
    public String generateUploadUrl(String storageKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentType(contentType)
                .build();

        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest putObjectPresignRequest = 
            software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60)) // URL valid for 1 hour
                .putObjectRequest(putObjectRequest)
                .build();

        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedPutObjectRequest = 
            s3Presigner.presignPutObject(putObjectPresignRequest);
            
        return presignedPutObjectRequest.url().toString();
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete object from R2: {}", storageKey, e);
        }
    }

    @Override
    public String startMultipartUpload(String storageKey) {
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build());
        return response.uploadId();
    }

    @Override
    public String uploadPart(String storageKey, String uploadId, int partNumber, MultipartFile file) {
        try {
            UploadPartResponse response = s3Client.uploadPart(UploadPartRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return response.eTag();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read part data", e);
        }
    }

    @Override
    public void completeMultipartUpload(String storageKey, String uploadId, Map<Integer, String> partEtags) {
        List<CompletedPart> completedParts = partEtags.entrySet().stream()
                .map(entry -> CompletedPart.builder()
                        .partNumber(entry.getKey())
                        .eTag(entry.getValue())
                        .build())
                .collect(Collectors.toList());

        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .uploadId(uploadId)
                .multipartUpload(completedMultipartUpload)
                .build());
    }
}
