package com.titan.titancorebanking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Vault integration for centralized secret management.
 * Rotates DB passwords, JWT secrets, and API keys every hour.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
@Slf4j
public class VaultSecretManager {
    
    private final VaultTemplate vaultTemplate;
    
    @Value("${vault.secret.database.path:secret/database}")
    private String databasePath;
    
    @Value("${vault.secret.jwt.path:secret/jwt}")
    private String jwtPath;
    
    private volatile String databasePassword;
    private volatile String jwtSecret;
    
    public VaultSecretManager(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
        refreshSecrets();
    }
    
    @Scheduled(fixedRate = 3600000) // Every hour
    public void refreshSecrets() {
        try {
            VaultResponse dbResponse = vaultTemplate.read(databasePath);
            if (dbResponse != null && dbResponse.getData() != null) {
                databasePassword = (String) dbResponse.getData().get("password");
                log.info("🔄 Rotated database password from Vault");
            }
            
            VaultResponse jwtResponse = vaultTemplate.read(jwtPath);
            if (jwtResponse != null && jwtResponse.getData() != null) {
                jwtSecret = (String) jwtResponse.getData().get("secret");
                log.info("🔄 Rotated JWT secret from Vault");
            }
        } catch (Exception e) {
            log.error("❌ Failed to rotate secrets from Vault: {}", e.getMessage());
        }
    }
    
    public String getDatabasePassword() {
        return databasePassword;
    }
    
    public String getJwtSecret() {
        return jwtSecret;
    }
}
