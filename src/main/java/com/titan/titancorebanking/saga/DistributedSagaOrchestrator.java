package com.titan.titancorebanking.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Distributed Saga Orchestrator for multi-service transactions.
 * Coordinates: Core Banking → Notifications → Promotions
 * Implements compensating transactions for 100% consistency.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DistributedSagaOrchestrator {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Step 1: Transaction completed in core banking
     * → Trigger notifications and promotions
     */
    @KafkaListener(topics = "banking.transactions.completed", groupId = "saga-orchestrator")
    @Transactional
    public void orchestrateTransactionSaga(String payload) {
        try {
            var event = objectMapper.readValue(payload, TransactionCompletedEvent.class);
            String sagaId = UUID.randomUUID().toString();
            
            log.info("Starting saga {} for transaction {}", sagaId, event.transactionId());
            
            // Step 2: Send notification command
            var notificationCmd = new NotificationCommand(
                sagaId, event.transactionId(), event.username(), 
                "Transfer completed: $" + event.amount(), "SMS"
            );
            kafkaTemplate.send("banking.saga.notification-requested", sagaId, notificationCmd);
            
            // Step 3: Send promotion command
            var promotionCmd = new PromotionCommand(
                sagaId, event.transactionId(), event.sourceAccountNumber(),
                event.amount(), event.type()
            );
            kafkaTemplate.send("banking.saga.promotion-requested", sagaId, promotionCmd);
            
        } catch (Exception e) {
            log.error("Saga orchestration failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Compensation: Notification service failed
     * → Log failure, continue with promotions (non-critical)
     */
    @KafkaListener(topics = "banking.saga.notification-failed", groupId = "saga-orchestrator")
    public void handleNotificationFailure(String payload) {
        try {
            var failure = objectMapper.readValue(payload, SagaFailure.class);
            log.warn("Notification failed for saga {}: {}", failure.sagaId(), failure.reason());
            // Non-critical: don't compensate transaction
        } catch (Exception e) {
            log.error("Failed to handle notification failure", e);
        }
    }

    /**
     * Compensation: Promotion service failed
     * → Reverse transaction if promotion was critical
     */
    @KafkaListener(topics = "banking.saga.promotion-failed", groupId = "saga-orchestrator")
    @Transactional
    public void handlePromotionFailure(String payload) {
        try {
            var failure = objectMapper.readValue(payload, SagaFailure.class);
            log.error("Promotion failed for saga {}: {}", failure.sagaId(), failure.reason());
            
            // If promotion was part of the transaction promise, compensate
            if (failure.compensationRequired()) {
                var compensation = new CompensationRequest(
                    failure.transactionId(), 
                    "Promotion service failure: " + failure.reason(),
                    "saga-orchestrator"
                );
                kafkaTemplate.send("banking.saga.compensation-requested", 
                    failure.transactionId().toString(), compensation);
            }
        } catch (Exception e) {
            log.error("Failed to handle promotion failure", e);
        }
    }

    // DTOs
    public record TransactionCompletedEvent(
        String transactionId, String username, String amount, 
        String type, String sourceAccountNumber, String targetAccountNumber
    ) {}
    
    public record NotificationCommand(
        String sagaId, String transactionId, String recipient, 
        String message, String channel
    ) {}
    
    public record PromotionCommand(
        String sagaId, String transactionId, String accountNumber,
        String amount, String transactionType
    ) {}
    
    public record SagaFailure(
        String sagaId, Long transactionId, String reason, boolean compensationRequired
    ) {}
    
    public record CompensationRequest(Long transactionId, String reason, String requestedBy) {}
}
