package com.titan.titancorebanking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.titancorebankingevent.service.TransactionCompletedEvent;
import com.titan.titancorebanking.model.Account;
import com.titan.titancorebanking.model.OutboxEvent;
import com.titan.titancorebanking.model.Transaction;
import com.titan.titancorebanking.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventPublisherService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 🚀 Publish Transaction Event via Outbox Pattern
     * Guarantees atomicity: DB commit = Event guaranteed delivery
     */
    public void publishTransactionCompletedEvent(Transaction transaction) {
        try {
            TransactionCompletedEvent event = buildTransactionCompletedEvent(transaction);
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(transaction.getId().toString())
                .aggregateType("Transaction")
                .eventType("TransactionCompleted")
                .payload(payload)
                .build();
            
            outboxRepository.save(outbox);
            
            log.info("✅ Event saved to outbox: TX ID: {}", transaction.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to save event to outbox: TX ID: {}", transaction.getId(), e);
            throw new RuntimeException("Failed to save event", e);
        }
    }

    private TransactionCompletedEvent buildTransactionCompletedEvent(Transaction tx) {
        // ✅ SMART ACCOUNT DETECTION
        // Transaction អាចមាន fromAccount ឬ toAccount អាស្រ័យលើ Type
        Account primaryAccount = tx.getFromAccount() != null ? tx.getFromAccount() : tx.getToAccount();

        String username = (primaryAccount != null && primaryAccount.getUser() != null)
                ? primaryAccount.getUser().getUsername()
                : "SYSTEM";

        String currency = (primaryAccount != null)
                ? primaryAccount.getCurrency().name()
                : "USD";

        // Get Correlation ID for Tracing
        String correlationId = MDC.get("correlationId") != null ? MDC.get("correlationId") : UUID.randomUUID().toString();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "titan-core-banking");
        metadata.put("channel", "mobile-app");

        return TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TransactionCompleted")
                .transactionId(String.valueOf(tx.getId()))
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .amount(tx.getAmount())
                .currency(currency)
                .type(tx.getTransactionType().name())
                .status(tx.getStatus().name())
                .sourceAccountNumber(tx.getFromAccount() != null ? tx.getFromAccount().getAccountNumber() : null)
                .targetAccountNumber(tx.getToAccount() != null ? tx.getToAccount().getAccountNumber() : null)
                .username(username)
                .note(tx.getNote())
                .metadata(metadata)
                .build();
    }
}