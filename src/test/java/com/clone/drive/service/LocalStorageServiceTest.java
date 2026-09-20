package com.clone.drive.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageServiceTest {

    @TempDir
    Path tempStorageDir;

    private LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tempStorageDir.toString());
    }

    @Test
    void testNormalStorageAndRetrieval() {
        String key = "user-123/file.txt";
        byte[] content = "Hello World".getBytes();

        storageService.store(new ByteArrayInputStream(content), key);
        assertNotNull(storageService.loadAsResource(key));
    }

    @Test
    void testPathTraversalInStoreThrowsSecurityException() {
        byte[] content = "malicious payload".getBytes();

        assertThrows(SecurityException.class, () -> 
            storageService.store(new ByteArrayInputStream(content), "../../etc/passwd")
        );

        assertThrows(SecurityException.class, () -> 
            storageService.store(new ByteArrayInputStream(content), "user/../../secret.txt")
        );

        assertThrows(SecurityException.class, () -> 
            storageService.store(new ByteArrayInputStream(content), "/etc/shadow")
        );
    }

    @Test
    void testPathTraversalInLoadAsResourceThrowsSecurityException() {
        assertThrows(SecurityException.class, () -> 
            storageService.loadAsResource("../outside.txt")
        );

        assertThrows(SecurityException.class, () -> 
            storageService.loadAsResource("user/../../../../windows/win.ini")
        );
    }

    @Test
    void testPathTraversalInDeleteThrowsSecurityException() {
        assertThrows(SecurityException.class, () -> 
            storageService.delete("../../root_file")
        );
    }

    @Test
    void testPathTraversalInUploadPartThrowsSecurityException() {
        MockMultipartFile file = new MockMultipartFile("file", "part.bin", "application/octet-stream", "data".getBytes());

        assertThrows(SecurityException.class, () -> 
            storageService.uploadPart("../../../etc/cron.d/job", "upload-1", 1, file)
        );

        assertThrows(SecurityException.class, () -> 
            storageService.uploadPart("user/../../escaped.bin", "upload-1", 1, file)
        );
    }
}
