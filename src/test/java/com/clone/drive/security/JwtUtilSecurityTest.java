package com.clone.drive.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilSecurityTest {

    @Test
    void testDevProfileAllowsDefaultSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        JwtUtil jwtUtil = new JwtUtil(env);
        ReflectionTestUtils.setField(jwtUtil, "secret", "SuperSecretKeyForDevelopmentOnlyDoNotUseInProd12345!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);

        assertDoesNotThrow(jwtUtil::validateSecret);
    }

    @Test
    void testProdProfileBlocksDefaultSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtUtil jwtUtil = new JwtUtil(env);
        ReflectionTestUtils.setField(jwtUtil, "secret", "SuperSecretKeyForDevelopmentOnlyDoNotUseInProd12345!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtUtil::validateSecret);
        assertTrue(ex.getMessage().contains("default development JWT secret"));
    }

    @Test
    void testProdProfileBlocksShortSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        JwtUtil jwtUtil = new JwtUtil(env);
        ReflectionTestUtils.setField(jwtUtil, "secret", "TooShortKey123");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtUtil::validateSecret);
        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }

    @Test
    void testProdProfileBlocksLowEntropySecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtUtil jwtUtil = new JwtUtil(env);
        ReflectionTestUtils.setField(jwtUtil, "secret", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtUtil::validateSecret);
        assertTrue(ex.getMessage().contains("lacks sufficient entropy"));
    }

    @Test
    void testProdProfileAcceptsStrongSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtUtil jwtUtil = new JwtUtil(env);
        ReflectionTestUtils.setField(jwtUtil, "secret", "c2VjdXJlX3JhbmRvbV9zZWNyZXRfa2V5X2Zvcl9wcm9kXzIwMjYhQCM=");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);

        assertDoesNotThrow(jwtUtil::validateSecret);
    }
}
