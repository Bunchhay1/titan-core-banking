package com.titan.titancorebankingevent.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCompletedEvent {
    private String eventId;
    private String eventType;
    private String transactionId;
    private Instant timestamp;
    private String correlationId;

    // Payload Data
    private BigDecimal amount;
    private String currency;
    private String type;     // TRANSFER, DEPOSIT, WITHDRAWAL
    private String status;   // SUCCESS, FAILED
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private String username;
    private String note;

    private Map<String, String> metadata;
}