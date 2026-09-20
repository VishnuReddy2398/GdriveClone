package com.clone.drive.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for handling Envelope Encryption.
 * Generates Data Encryption Keys (DEKs), wraps them with a Master Key (KEK),
 * and provides encrypting/decrypting streams.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey kek; // Master Key Encryption Key

    public EncryptionService(@Value("${security.master.key}") String masterKeyStr) {
        if (masterKeyStr == null || masterKeyStr.length() < 32) {
            throw new IllegalArgumentException("Master key must be at least 32 characters long for AES-256");
        }
        // Use the first 32 bytes for AES-256
        byte[] keyBytes = masterKeyStr.substring(0, 32).getBytes();
        this.kek = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public SecretKey generateDek() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(256, new SecureRandom());
        return keyGenerator.generateKey();
    }

    public byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public String wrapDek(SecretKey dek) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); // Key wrapping doesn't need GCM here, but could use AESWrap
        cipher.init(Cipher.WRAP_MODE, kek);
        byte[] wrappedKey = cipher.wrap(dek);
        return Base64.getEncoder().encodeToString(wrappedKey);
    }

    public SecretKey unwrapDek(String wrappedDekStr) throws Exception {
        byte[] wrappedDek = Base64.getDecoder().decode(wrappedDekStr);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.UNWRAP_MODE, kek);
        return (SecretKey) cipher.unwrap(wrappedDek, ALGORITHM, Cipher.SECRET_KEY);
    }

    public CipherOutputStream getEncryptingOutputStream(OutputStream targetStream, SecretKey dek, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, dek, parameterSpec);
        return new CipherOutputStream(targetStream, cipher);
    }

    public CipherInputStream getEncryptingInputStream(InputStream sourceStream, SecretKey dek, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, dek, parameterSpec);
        return new CipherInputStream(sourceStream, cipher);
    }

    public CipherInputStream getDecryptingInputStream(InputStream sourceStream, SecretKey dek, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, dek, parameterSpec);
        return new CipherInputStream(sourceStream, cipher);
    }
}
