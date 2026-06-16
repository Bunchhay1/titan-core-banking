package com.titan.titancorebanking.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;

/**
 * JPA AttributeConverter for automatic PII encryption/decryption.
 * Use with @Convert(converter = PiiConverter.class) on entity fields.
 */
@Converter(autoApply = false)
public class PiiConverter implements AttributeConverter<String, String> {
    
    @Value("${pii.encryption.key}")
    private String encryptionKey;
    
    private PiiEncryption piiEncryption;
    
    private PiiEncryption getPiiEncryption() {
        if (piiEncryption == null) {
            piiEncryption = new PiiEncryption(encryptionKey);
        }
        return piiEncryption;
    }
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return getPiiEncryption().encrypt(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return getPiiEncryption().decrypt(dbData);
    }
}
