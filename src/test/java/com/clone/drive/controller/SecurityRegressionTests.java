package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Permission;
import com.clone.drive.domain.PublicLink;
import com.clone.drive.domain.User;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.PublicLinkRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.security.CustomUserDetailsService;
import com.clone.drive.security.JwtUtil;
import com.clone.drive.service.AuthorizationService;
import com.clone.drive.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityRegressionTests {

    private FileRecordRepository fileRecordRepository;
    private UserRepository userRepository;
    private StorageService storageService;
    private AuthorizationService authService;
    private PublicLinkRepository publicLinkRepository;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redisTemplate;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        fileRecordRepository = mock(FileRecordRepository.class);
        userRepository = mock(UserRepository.class);
        storageService = mock(StorageService.class);
        authService = mock(AuthorizationService.class);
        publicLinkRepository = mock(PublicLinkRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(StringRedisTemplate.class);

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("usera@example.com");
        userA.setName("User A");

        userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setEmail("userb@example.com");
        userB.setName("User B");

        when(userRepository.findByEmail("usera@example.com")).thenReturn(Optional.of(userA));
        when(userRepository.findByEmail("userb@example.com")).thenReturn(Optional.of(userB));

        // Set authenticated user to userA
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userA, null, userA.getAuthorities())
        );
    }

    @Test
    void testFileControllerUploadForbiddenForOtherUserStorage() {
        FileController controller = new FileController(
                fileRecordRepository, null, null, userRepository, storageService, null, authService, null
        );

        // User A attempts to upload into User B's folder/prefix
        ResponseEntity<?> response = controller.uploadLocalFile(
                userB.getId().toString(), "malicious.exe", new ByteArrayInputStream("payload".getBytes())
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(storageService, never()).store(any(InputStream.class), anyString());
    }

    @Test
    void testMultipartUploadControllerPartUploadForbiddenForOtherUserStorageKey() {
        MultipartUploadController controller = new MultipartUploadController(
                storageService, fileRecordRepository, null, userRepository
        );

        MockMultipartFile file = new MockMultipartFile("file", "chunk.bin", "application/octet-stream", "data".getBytes());

        // User A attempts to upload a chunk into User B's storageKey
        String otherUserStorageKey = userB.getId().toString() + "/chunk-1";
        ResponseEntity<?> response = controller.uploadPart("upload-123", otherUserStorageKey, 1, file);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void testMediaControllerBlocksInlinePreviewOfSvgAndHtml() {
        MediaController controller = new MediaController(
                fileRecordRepository, userRepository, storageService, authService
        );

        FileRecord svgRecord = new FileRecord();
        svgRecord.setId(UUID.randomUUID());
        svgRecord.setName("image.svg");
        svgRecord.setMimeType("image/svg+xml");
        svgRecord.setOwner(userA);

        when(fileRecordRepository.findById(svgRecord.getId())).thenReturn(Optional.of(svgRecord));
        when(authService.canAccessFile(userA, svgRecord, Permission.PermissionLevel.VIEWER)).thenReturn(true);

        ResponseEntity<?> response = controller.previewMedia(svgRecord.getId());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());

        FileRecord htmlRecord = new FileRecord();
        htmlRecord.setId(UUID.randomUUID());
        htmlRecord.setName("index.html");
        htmlRecord.setMimeType("text/html");
        htmlRecord.setOwner(userA);

        when(fileRecordRepository.findById(htmlRecord.getId())).thenReturn(Optional.of(htmlRecord));
        when(authService.canAccessFile(userA, htmlRecord, Permission.PermissionLevel.VIEWER)).thenReturn(true);

        ResponseEntity<?> htmlResponse = controller.previewMedia(htmlRecord.getId());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, htmlResponse.getStatusCode());
    }

    @Test
    void testMediaControllerAllowsSafeImagesWithCspAndNoSniff() {
        MediaController controller = new MediaController(
                fileRecordRepository, userRepository, storageService, authService
        );

        FileRecord pngRecord = new FileRecord();
        pngRecord.setId(UUID.randomUUID());
        pngRecord.setName("photo.png");
        pngRecord.setMimeType("image/png");
        pngRecord.setOwner(userA);
        pngRecord.setStorageKey("userA/photo.png");

        when(fileRecordRepository.findById(pngRecord.getId())).thenReturn(Optional.of(pngRecord));
        when(authService.canAccessFile(userA, pngRecord, Permission.PermissionLevel.VIEWER)).thenReturn(true);
        when(storageService.loadAsResource(pngRecord.getStorageKey())).thenReturn(new ByteArrayResource("image-bytes".getBytes()));

        ResponseEntity<?> response = controller.previewMedia(pngRecord.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", response.getHeaders().getFirst("Content-Security-Policy"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("inline"));
    }

    @Test
    void testPublicLinkRateLimitingBlocksExcessivePasswordAttempts() {
        PublicLinkController controller = new PublicLinkController(
                publicLinkRepository, fileRecordRepository, userRepository, storageService, passwordEncoder, redisTemplate
        );

        String token = "test-token-123";
        PublicLink link = new PublicLink();
        link.setToken(token);
        link.setPasswordHash("hashed_pwd");
        FileRecord file = new FileRecord();
        file.setName("document.pdf");
        link.setFileRecord(file);

        when(publicLinkRepository.findByToken(token)).thenReturn(Optional.of(link));
        when(passwordEncoder.matches("wrong_pass", "hashed_pwd")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.42");

        // First 5 attempts should return 401 Unauthorized
        for (int i = 0; i < 5; i++) {
            ResponseEntity<?> response = controller.authenticatePublicLink(token, Map.of("password", "wrong_pass"), request);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "Attempt " + (i + 1) + " should be 401");
        }

        // 6th attempt from the same IP + token should be blocked with 429 Too Many Requests
        ResponseEntity<?> blockedResponse = controller.authenticatePublicLink(token, Map.of("password", "wrong_pass"), request);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blockedResponse.getStatusCode());
    }

    @Test
    void testDownloadLocalFileUnauthorizedReturnsForbidden() {
        FileController controller = new FileController(
                fileRecordRepository, null, null, userRepository, storageService, null, authService, null
        );

        String storageKey = userB.getId().toString() + "/secret.pdf";
        FileRecord fileB = new FileRecord();
        fileB.setName("secret.pdf");
        fileB.setOwner(userB);
        fileB.setStorageKey(storageKey);

        when(fileRecordRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(fileB));
        when(authService.canAccessFile(userA, fileB, Permission.PermissionLevel.VIEWER)).thenReturn(false);

        ResponseEntity<?> response = controller.downloadLocalFile(userB.getId().toString(), "secret.pdf");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testDownloadLocalFileDeletedReturnsNotFound() {
        FileController controller = new FileController(
                fileRecordRepository, null, null, userRepository, storageService, null, authService, null
        );

        String storageKey = userA.getId().toString() + "/deleted.pdf";
        FileRecord deletedFile = new FileRecord();
        deletedFile.setName("deleted.pdf");
        deletedFile.setOwner(userA);
        deletedFile.setStorageKey(storageKey);
        deletedFile.setDeletedAt(java.time.LocalDateTime.now());

        when(fileRecordRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(deletedFile));

        ResponseEntity<?> response = controller.downloadLocalFile(userA.getId().toString(), "deleted.pdf");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testWebSocketStompConnectWithoutTokenFails() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        com.clone.drive.config.WebSocketConfig wsConfig = new com.clone.drive.config.WebSocketConfig(jwtUtil, userDetailsService);

        org.springframework.messaging.simp.config.ChannelRegistration registration = new org.springframework.messaging.simp.config.ChannelRegistration();
        wsConfig.configureClientInboundChannel(registration);

        // Extract interceptor using reflection
        java.util.List<?> interceptors = (java.util.List<?>) org.springframework.test.util.ReflectionTestUtils.getField(registration, "interceptors");
        assertNotNull(interceptors);
        org.springframework.messaging.support.ChannelInterceptor interceptor = (org.springframework.messaging.support.ChannelInterceptor) interceptors.get(0);

        // STOMP CONNECT with no headers
        org.springframework.messaging.simp.stomp.StompHeaderAccessor connectAccessor = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
        org.springframework.messaging.Message<?> connectMsg = org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> 
            interceptor.preSend(connectMsg, null)
        );
    }

    @Test
    void testWebSocketStompSubscribeToOtherUserTopicFails() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        com.clone.drive.config.WebSocketConfig wsConfig = new com.clone.drive.config.WebSocketConfig(jwtUtil, userDetailsService);

        org.springframework.messaging.simp.config.ChannelRegistration registration = new org.springframework.messaging.simp.config.ChannelRegistration();
        wsConfig.configureClientInboundChannel(registration);

        java.util.List<?> interceptors = (java.util.List<?>) org.springframework.test.util.ReflectionTestUtils.getField(registration, "interceptors");
        org.springframework.messaging.support.ChannelInterceptor interceptor = (org.springframework.messaging.support.ChannelInterceptor) interceptors.get(0);

        // User A attempts to subscribe to User B's notifications
        org.springframework.messaging.simp.stomp.StompHeaderAccessor subAccessor = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/topic/user." + userB.getId().toString() + ".notifications");
        subAccessor.setUser(new UsernamePasswordAuthenticationToken(userA, null, userA.getAuthorities()));

        org.springframework.messaging.Message<?> subMsg = org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> 
            interceptor.preSend(subMsg, null)
        );

        // User A subscribes to User A's notifications -> allowed
        org.springframework.messaging.simp.stomp.StompHeaderAccessor ownSubAccessor = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        ownSubAccessor.setDestination("/topic/user." + userA.getId().toString() + ".notifications");
        ownSubAccessor.setUser(new UsernamePasswordAuthenticationToken(userA, null, userA.getAuthorities()));
        org.springframework.messaging.Message<?> ownSubMsg = org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], ownSubAccessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(ownSubMsg, null));
    }
}
