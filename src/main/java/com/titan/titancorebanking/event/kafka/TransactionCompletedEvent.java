package com.titan.titancorebanking.event.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Kafka Event: Transaction Completed
 * 
 * Published to: banking.transactions.completed
 * Purpose: Notify auxiliary services (Promotions, Gifts, Notifications) of completed transactions
 * Pattern: Fire-and-forget (async, non-blocking)
 * 
 * @author Bro (The Architect)
 * @date 2026-02-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCompletedEvent {
    
    // ========== Event Metadata ==========
    
    /**
     * Unique event identifier (UUID) for idempotency checks
     */
    private String eventId;
    
    /**
     * Event type identifier
     */
    @Builder.Default
    private String eventType = "TransactionCompleted";
    
    /**
     * Event schema version for backward compatibility
     */
    @Builder.Default
    private String eventVersion = "1.0";
    
    /**
     * Event timestamp in ISO-8601 format
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;
    
    /**
     * Correlation ID for distributed tracing
     */
    private String correlationId;
    
    // ========== Transaction Data ==========
    
    /**
     * Transaction ID from database
     */
    private Long transactionId;
    
    /**
     * Account ID involved in the transaction
     */
    private Long accountId;
    
    /**
     * Transaction amount
     */
    private BigDecimal amount;
    
    /**
     * Currency code (ISO 4217: USD, KHR, EUR, etc.)
     */
    private String currency;
    
    /**
     * Transaction type (DEPOSIT, WITHDRAWAL, TRANSFER, etc.)
     */
    private String transactionType;
    
    /**
     * Transaction status (COMPLETED, FAILED, PENDING)
     */
    private String status;
    
    // ========== Optional Metadata ==========
    
    /**
     * Transaction description/note
     */
    private String description;
    
    /**
     * Additional metadata (channel, device, etc.)
     */
    private Map<String, String> metadata;
    
    /**
     * Username of the account owner
     */
    private String username;
    
    /**
     * Recipient account number (for transfers)
     */
    private String toAccountNumber;
}
