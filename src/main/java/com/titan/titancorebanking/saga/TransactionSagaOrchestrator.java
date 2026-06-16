package com.titan.titancorebanking.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.titancorebanking.enums.TransactionStatus;
import com.titan.titancorebanking.model.Transaction;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saga Orchestrator — Event Choreography pattern.
 *
 * Flow:
 *   core-banking publishes TransactionCompleted
 *     → notifications-service consumes, sends alert
 *     → promotions-service consumes, grants reward
 *
 * Compensation:
 *   If promotions rejects (ledger-reward-rejected), we reverse the ledger debit here.
 *   If notifications fails (banking.notifications.dlq), we log and alert ops — no ledger reversal needed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionSagaOrchestrator {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Step: Promotions service rejected the reward after ledger committed.
     * Compensation: mark transaction COMPENSATED, reverse balances.
     */
    @KafkaListener(topics = "banking.saga.compensation-requested", groupId = "saga-orchestrator")
    @Transactional
    public void handleCompensationRequest(String payload) {
        try {
            CompensationRequest req = objectMapper.readValue(payload, CompensationRequest.class);
            log.warn("Saga compensation requested for TX {}: {}", req.transactionId(), req.reason());

            Transaction tx = transactionRepository.findById(req.transactionId())
                    .orElseThrow(() -> new IllegalStateException("TX not found: " + req.transactionId()));

            if (tx.getStatus() == TransactionStatus.COMPENSATED) {
                log.info("TX {} already compensated, skipping", req.transactionId());
                return;
            }

            // Reverse the balances
            if (tx.getFromAccount() != null && tx.getToAccount() != null) {
                var from = tx.getFromAccount();
                var to = tx.getToAccount();
                BigDecimal amount = tx.getAmount();

                from.setBalance(from.getBalance().add(amount));
                to.setBalance(to.getBalance().subtract(amount));

                accountRepository.save(from);
                accountRepository.save(to);
            }

            tx.setStatus(TransactionStatus.COMPENSATED);
            transactionRepository.save(tx);

            // Notify downstream that compensation is done
            kafkaTemplate.send("banking.saga.compensation-completed",
                    tx.getId().toString(),
                    new CompensationCompleted(tx.getId(), req.reason()));

            log.info("Saga compensation completed for TX {}", tx.getId());

        } catch (Exception e) {
            log.error("CRITICAL: Saga compensation failed for payload {}: {}", payload, e.getMessage(), e);
            // Push to DLQ for manual intervention
            kafkaTemplate.send("banking.saga.compensation-dlq", payload);
        }
    }

    public record CompensationRequest(Long transactionId, String reason, String requestedBy) {}
    public record CompensationCompleted(Long transactionId, String reason) {}
}
