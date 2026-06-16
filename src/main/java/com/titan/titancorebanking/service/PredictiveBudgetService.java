package com.titan.titancorebanking.service;

import com.titan.titancorebanking.model.Account;
import com.titan.titancorebanking.model.Transaction;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Predictive Budget Alert Service using Virtual Threads (Java 21).
 * Analyzes spending patterns and predicts account exhaustion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PredictiveBudgetService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void analyzeBudgetRisks() {
        List<Account> activeAccounts = accountRepository.findAll()
            .stream()
            .filter(account -> account.getBalance().compareTo(new BigDecimal("1000")) > 0)
            .toList();

        log.info("Analyzing budget risks for {} accounts", activeAccounts.size());

        // Use Virtual Threads for concurrent analysis
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = activeAccounts.stream()
                .map(account -> CompletableFuture.runAsync(() -> 
                    analyzeAccountBudget(account), executor))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    private void analyzeAccountBudget(Account account) {
        try {
            // Calculate daily spending average (last 7 days)
            LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
            List<com.titan.titancorebanking.model.Transaction> recentTransactions = transactionRepository
                .findByFromAccountAndTimestampAfter(account, weekAgo);

            if (recentTransactions.isEmpty()) {
                return; // No spending pattern
            }

            BigDecimal totalSpent = recentTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal dailyAverage = totalSpent.divide(new BigDecimal("7"), 2, 
                java.math.RoundingMode.HALF_UP);

            // Predict exhaustion time
            BigDecimal currentBalance = account.getBalance();
            if (dailyAverage.compareTo(BigDecimal.ZERO) > 0) {
                double daysUntilExhaustion = currentBalance.divide(dailyAverage, 2, 
                    java.math.RoundingMode.HALF_UP).doubleValue();

                // Alert if balance will be exhausted within 2 hours at current rate
                if (daysUntilExhaustion <= 0.083) { // 2 hours = 0.083 days
                    sendBudgetAlert(account, daysUntilExhaustion, dailyAverage);
                }
            }

        } catch (Exception e) {
            log.error("Failed to analyze budget for account {}: {}", 
                account.getAccountNumber(), e.getMessage());
        }
    }

    private void sendBudgetAlert(Account account, double hoursUntilExhaustion, BigDecimal dailySpend) {
        var alert = new BudgetAlert(
            account.getUser().getUsername(),
            account.getAccountNumber(),
            account.getBalance(),
            dailySpend,
            hoursUntilExhaustion,
            "CRITICAL",
            LocalDateTime.now()
        );

        kafkaTemplate.send("banking.budget-alerts", account.getAccountNumber(), alert);
        
        log.warn("🚨 Budget alert: Account {} will be exhausted in {:.1f} hours at current spending rate", 
            account.getAccountNumber(), hoursUntilExhaustion * 24);
    }

    public record BudgetAlert(
        String username,
        String accountNumber,
        BigDecimal currentBalance,
        BigDecimal dailySpendRate,
        double hoursUntilExhaustion,
        String severity,
        LocalDateTime timestamp
    ) {}
}
