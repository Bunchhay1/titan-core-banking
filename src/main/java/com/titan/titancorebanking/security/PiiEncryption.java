package com.titan.titancorebanking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for PII data at rest.
 * Uses authenticated encryption to prevent tampering.
 */
@Component
@Slf4j
public class PiiEncryption {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;
    
    public PiiEncryption(@Value("${pii.encryption.key:${vault.secret.pii.key:TitanPII-AES256-SecureKey-32Bytes}}") String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("PII encryption key must be exactly 32 bytes");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        this.secureRandom = new SecureRandom();
    }
    
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
        } catch (Exception e) {
            log.error("Failed to encrypt PII data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }
        
        try {
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decodedData, 0, iv, 0, iv.length);
            
            // Extract encrypted data
            byte[] encrypted = new byte[decodedData.length - GCM_IV_LENGTH];
            System.arraycopy(decodedData, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encrypted);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt PII data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
