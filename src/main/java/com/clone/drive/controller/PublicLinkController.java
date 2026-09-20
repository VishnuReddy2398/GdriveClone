package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.PublicLink;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.PublicLinkRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PublicLinkController {

    private final Map<String, Bucket> linkAuthBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> globalIpBuckets = new ConcurrentHashMap<>();

    private Bucket resolveLinkAuthBucket(String ip, String token) {
        return linkAuthBuckets.computeIfAbsent(ip + ":" + token, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
                .build());
    }

    private Bucket resolveGlobalIpBucket(String ip) {
        return globalIpBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1))))
                .build());
    }

    private final PublicLinkRepository publicLinkRepository;
    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    public PublicLinkController(PublicLinkRepository publicLinkRepository,
                                FileRecordRepository fileRecordRepository,
                                UserRepository userRepository,
                                StorageService storageService,
                                PasswordEncoder passwordEncoder,
                                StringRedisTemplate redisTemplate) {
        this.publicLinkRepository = publicLinkRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Create a public share link for a file (authenticated).
     */
    @PostMapping("/api/public-links")
    public ResponseEntity<?> createPublicLink(
            @RequestParam UUID fileId,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) Integer expiresInHours) {

        User user = getAuthenticatedUser();
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the file owner can create public links"));
        }

        if (file.getDeletedAt() != null) {
            return ResponseEntity.status(400).body(Map.of("error", "Cannot share a deleted file"));
        }

        PublicLink link = new PublicLink();
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setFileRecord(file);

        if (password != null && !password.isEmpty()) {
            link.setPasswordHash(passwordEncoder.encode(password));
        }

        if (expiresInHours != null) {
            link.setExpiresAt(LocalDateTime.now().plusHours(expiresInHours));
        }

        publicLinkRepository.save(link);

        return ResponseEntity.ok(Map.of(
                "token", link.getToken(),
                "url", "/share/" + link.getToken(),
                "expiresAt", link.getExpiresAt() != null ? link.getExpiresAt().toString() : "never"
        ));
    }

    /**
     * Get file metadata via public link (unauthenticated).
     */
    @GetMapping("/api/public/links/{token}")
    public ResponseEntity<?> getPublicLinkInfo(@PathVariable String token) {
        PublicLink link = publicLinkRepository.findByToken(token)
                .orElse(null);

        if (link == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Link not found"));
        }

        if (link.isExpired()) {
            return ResponseEntity.status(410).body(Map.of("error", "This link has expired"));
        }

        FileRecord file = link.getFileRecord();
        if (file == null || file.getDeletedAt() != null) {
            return ResponseEntity.status(404).body(Map.of("error", "File not found or deleted"));
        }

        boolean requiresPassword = link.getPasswordHash() != null;

        return ResponseEntity.ok(Map.of(
                "fileName", file.getName(),
                "mimeType", file.getMimeType(),
                "size", file.getSize(),
                "requiresPassword", requiresPassword
        ));
    }

    /**
     * Authenticate with password for a protected public link (unauthenticated).
     * Protected with compound Bucket4j rate limiting (IP + token & global IP).
     * Issues a short-lived accessKey stored in Redis.
     */
    @PostMapping("/api/public/links/{token}/auth")
    public ResponseEntity<?> authenticatePublicLink(
            @PathVariable String token,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";

        // 1. Check global IP rate limit
        if (!resolveGlobalIpBucket(ip).tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many attempts from your IP. Please try again later."));
        }

        // 2. Check token-specific IP rate limit
        if (!resolveLinkAuthBucket(ip, token).tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many attempts for this link. Please try again later."));
        }

        PublicLink link = publicLinkRepository.findByToken(token)
                .orElse(null);

        if (link == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Link not found"));
        }

        if (link.isExpired()) {
            return ResponseEntity.status(410).body(Map.of("error", "This link has expired"));
        }

        if (link.getFileRecord() == null || link.getFileRecord().getDeletedAt() != null) {
            return ResponseEntity.status(404).body(Map.of("error", "File not found or deleted"));
        }

        String password = body.get("password");
        if (link.getPasswordHash() == null) {
            return ResponseEntity.ok(Map.of("authenticated", true));
        }

        if (password == null || !passwordEncoder.matches(password, link.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Incorrect password"));
        }

        // Generate a short-lived access key and store it in Redis with a 10-minute TTL
        String accessKey = UUID.randomUUID().toString();
        String redisKey = "public_link_access:" + token + ":" + accessKey;
        redisTemplate.opsForValue().set(redisKey, "authenticated", Duration.ofMinutes(10));

        return ResponseEntity.ok(Map.of("authenticated", true, "accessKey", accessKey));
    }

    /**
     * Download file via public link (unauthenticated).
     * If the link is password-protected, a valid accessKey (from /auth) is required.
     */
    @GetMapping("/api/public/links/{token}/download")
    public ResponseEntity<?> downloadViaPublicLink(
            @PathVariable String token,
            @RequestParam(required = false) String accessKey) {
        PublicLink link = publicLinkRepository.findByToken(token)
                .orElse(null);

        if (link == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Link not found"));
        }

        if (link.isExpired()) {
            return ResponseEntity.status(410).body(Map.of("error", "This link has expired"));
        }

        FileRecord file = link.getFileRecord();
        if (file == null || file.getDeletedAt() != null) {
            return ResponseEntity.status(404).body(Map.of("error", "File not found or deleted"));
        }

        // If password-protected, verify the accessKey via Redis
        if (link.getPasswordHash() != null) {
            if (accessKey == null || accessKey.isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", "This link is password-protected. Authenticate first."));
            }
            String redisKey = "public_link_access:" + token + ":" + accessKey;
            String value = redisTemplate.opsForValue().get(redisKey);
            if (value == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired access key. Re-authenticate."));
            }
        }

        // Increment download count
        link.setDownloadCount(link.getDownloadCount() + 1);
        publicLinkRepository.save(link);

        try {
            Resource resource = storageService.loadAsResource(file.getStorageKey());
            String disposition = ContentDisposition.attachment()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build()
                    .toString();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CONTENT_TYPE, file.getMimeType())
                    .body(resource);
        } catch (UnsupportedOperationException e) {
            // R2 mode — return a presigned URL instead
            String url = storageService.generateDownloadUrl(file.getStorageKey());
            return ResponseEntity.ok(Map.of("downloadUrl", url));
        }
    }
}
