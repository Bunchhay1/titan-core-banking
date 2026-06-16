package com.titan.titancorebanking.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security tests for PII encryption and RBAC.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "pii.encryption.key=TitanPII-AES256-SecureKey-32Bytes",
    "spring.cloud.vault.enabled=false"
})
class SecurityTest {

    @Test
    void piiEncryption_encryptsAndDecrypts_correctly() {
        PiiEncryption encryption = new PiiEncryption("TitanPII-AES256-SecureKey-32Bytes");
        
        String originalEmail = "john.doe@example.com";
        String encrypted = encryption.encrypt(originalEmail);
        String decrypted = encryption.decrypt(encrypted);
        
        assertThat(encrypted).isNotEqualTo(originalEmail);
        assertThat(encrypted).isNotEmpty();
        assertThat(decrypted).isEqualTo(originalEmail);
    }
    
    @Test
    void piiEncryption_handlesNullAndEmpty_gracefully() {
        PiiEncryption encryption = new PiiEncryption("TitanPII-AES256-SecureKey-32Bytes");
        
        assertThat(encryption.encrypt(null)).isNull();
        assertThat(encryption.encrypt("")).isEmpty();
        assertThat(encryption.decrypt(null)).isNull();
        assertThat(encryption.decrypt("")).isEmpty();
    }
}
