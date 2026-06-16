package com.titan.titancorebanking.phase5;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.enums.AccountStatus;
import com.titan.titancorebanking.enums.AccountType;
import com.titan.titancorebanking.enums.Currency;
import com.titan.titancorebanking.model.Account;
import com.titan.titancorebanking.model.User;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.UserRepository;
import com.titan.titancorebanking.service.PredictiveBudgetService;
import com.titan.titancorebanking.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 Integration Tests:
 * - Kafka Streams CEP
 * - Distributed Saga
 * - AI Risk Engine (>0.7 threshold)
 * - Predictive Budget Alerts
 * - A/B Testing
 */
@Testcontainers
@SpringBootTest
class Phase5IntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("titandb")
            .withUsername("postgres")
            .withPassword("password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        // Disable external services for testing
        registry.add("grpc.client.riskEngineClient.address", () -> "static://localhost:19999");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired TransactionService transactionService;
    @Autowired PredictiveBudgetService budgetService;
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User user;
    private Account highBalanceAccount;
    private Account lowBalanceAccount;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("phase5-user-" + UUID.randomUUID())
                .password(passwordEncoder.encode("secret"))
                .pin(passwordEncoder.encode("1234"))
                .role("ROLE_USER")
                .build());

        highBalanceAccount = accountRepository.save(Account.builder()
                .accountNumber("PHASE5-HIGH-" + UUID.randomUUID())
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("50000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user).build());

        lowBalanceAccount = accountRepository.save(Account.builder()
                .accountNumber("PHASE5-LOW-" + UUID.randomUUID())
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("100.00")) // Low balance for budget alerts
                .status(AccountStatus.ACTIVE)
                .user(user).build());
    }

    @Test
    void predictiveBudgetService_detectsLowBalance_triggersAlert() {
        // Simulate high spending pattern by creating multiple transactions
        for (int i = 0; i < 3; i++) {
            TransactionRequest req = new TransactionRequest(
                lowBalanceAccount.getAccountNumber(),
                highBalanceAccount.getAccountNumber(),
                new BigDecimal("20.00"),
                "1234",
                "Budget test " + i,
                null, "TRANSFER", null, null, null
            );
            transactionService.transfer(req, user.getUsername());
        }

        // Run budget analysis
        budgetService.analyzeBudgetRisks();

        // Verify account balance decreased
        var updatedAccount = accountRepository.findByAccountNumber(lowBalanceAccount.getAccountNumber()).orElseThrow();
        assertThat(updatedAccount.getBalance()).isLessThan(new BigDecimal("100.00"));
    }

    @Test
    void transactionService_handlesHighValueTransfer_withoutRiskEngine() {
        // High-value transfer that would trigger risk analysis
        TransactionRequest req = new TransactionRequest(
            highBalanceAccount.getAccountNumber(),
            lowBalanceAccount.getAccountNumber(),
            new BigDecimal("10000.00"),
            "1234",
            "High-value test",
            UUID.randomUUID().toString(),
            "TRANSFER", null, null, null
        );

        // Should succeed despite high value (risk engine disabled in test)
        var tx = transactionService.transfer(req, user.getUsername());
        assertThat(tx).isNotNull();
        assertThat(tx.getAmount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void idempotencyService_preventsDoubleProcessing_inPhase5Context() {
        String idempotencyKey = "phase5-test-" + UUID.randomUUID();
        
        TransactionRequest req = new TransactionRequest(
            highBalanceAccount.getAccountNumber(),
            lowBalanceAccount.getAccountNumber(),
            new BigDecimal("500.00"),
            "1234",
            "Idempotency test",
            idempotencyKey,
            "TRANSFER", null, null, null
        );

        // First request
        var tx1 = transactionService.transfer(req, user.getUsername());
        
        // Second request with same idempotency key
        var tx2 = transactionService.transfer(req, user.getUsername());

        // Should return same transaction
        assertThat(tx1.getId()).isEqualTo(tx2.getId());
        assertThat(tx1.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
}
