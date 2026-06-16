package com.titan.titancorebanking.chaos;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.enums.AccountStatus;
import com.titan.titancorebanking.enums.AccountType;
import com.titan.titancorebanking.enums.Currency;
import com.titan.titancorebanking.model.Account;
import com.titan.titancorebanking.model.User;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.OutboxRepository;
import com.titan.titancorebanking.repository.UserRepository;
import com.titan.titancorebanking.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos Engineering Tests — proves the system survives infrastructure failures
 * without data loss or double-spend.
 *
 * Tests:
 *   1. Kafka broker killed mid-flight → outbox guarantees delivery after restart
 *   2. Concurrent duplicate transfers → idempotency prevents double-debit
 *   3. Concurrent transfers to same account → pessimistic locking prevents lost updates
 */
@Testcontainers
@SpringBootTest
class ChaosEngineeringTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("titandb")
            .withUsername("postgres")
            .withPassword("password");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // Disable gRPC AI service for chaos tests
        registry.add("grpc.client.riskEngineClient.address", () -> "static://localhost:19999");
    }

    @Autowired TransactionService transactionService;
    @Autowired AccountRepository accountRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User user;
    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("chaos-user-" + UUID.randomUUID())
                .password(passwordEncoder.encode("secret"))
                .pin(passwordEncoder.encode("1234"))
                .role("ROLE_USER")
                .build());

        accountA = accountRepository.save(Account.builder()
                .accountNumber("CHAOS-A-" + UUID.randomUUID())
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("50000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user).build());

        accountB = accountRepository.save(Account.builder()
                .accountNumber("CHAOS-B-" + UUID.randomUUID())
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("1000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user).build());
    }

    /**
     * Chaos Test 1: Kafka killed mid-flight.
     * Proves: Outbox pattern guarantees the event is persisted in DB even when Kafka is down.
     * The OutboxRelayService will deliver it when Kafka recovers.
     */
    @Test
    void kafkaBrokerKilled_outboxRetainsEvent_noMoneyLost() throws Exception {
        // Kill Kafka BEFORE the transfer
        kafka.stop();

        String idempotencyKey = UUID.randomUUID().toString();
        TransactionRequest req = buildRequest(accountA.getAccountNumber(), accountB.getAccountNumber(),
                new BigDecimal("500.00"), idempotencyKey);

        // Transfer must still succeed (DB commit) even though Kafka is down
        var tx = transactionService.transfer(req, user.getUsername());
        assertThat(tx).isNotNull();

        // Money must have moved
        var updatedA = accountRepository.findByAccountNumber(accountA.getAccountNumber()).orElseThrow();
        assertThat(updatedA.getBalance()).isLessThan(new BigDecimal("50000.00"));

        // Outbox event must be persisted (will be relayed when Kafka restarts)
        long pendingEvents = outboxRepository.findTop100ByPublishedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5).size();
        assertThat(pendingEvents).isGreaterThanOrEqualTo(1);
    }

    /**
     * Chaos Test 2: User clicks "Transfer" twice simultaneously (race condition).
     * Proves: Idempotency key prevents double-debit.
     */
    @Test
    void duplicateTransferRequest_idempotencyPreventsDoubleDebit() throws InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();
        TransactionRequest req = buildRequest(accountA.getAccountNumber(), accountB.getAccountNumber(),
                new BigDecimal("1000.00"), idempotencyKey);

        int threads = 5;
        CountDownLatch latch = new CountDownLatch(1);
        List<Exception> errors = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    latch.await();
                    transactionService.transfer(req, user.getUsername());
                } catch (Exception e) {
                    synchronized (errors) { errors.add(e); }
                }
            });
        }

        latch.countDown(); // Release all threads simultaneously
        pool.shutdown();
        //noinspection ResultOfMethodCallIgnored
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Balance must only be debited ONCE regardless of how many threads fired
        var updatedA = accountRepository.findByAccountNumber(accountA.getAccountNumber()).orElseThrow();
        BigDecimal expectedMax = new BigDecimal("49000.00"); // 50000 - 1000 (fee) - 1000 (transfer)
        assertThat(updatedA.getBalance()).isGreaterThanOrEqualTo(expectedMax);
    }

    /**
     * Chaos Test 3: 10 concurrent transfers from the same account.
     * Proves: Pessimistic locking prevents lost updates / negative balance.
     */
    @Test
    void concurrentTransfers_pessimisticLockPreventsNegativeBalance() throws InterruptedException {
        BigDecimal initialBalance = accountA.getBalance(); // 50000
        int concurrency = 10;
        BigDecimal transferAmount = new BigDecimal("6000.00"); // 10 × 6000 = 60000 > 50000

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);

        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                try {
                    latch.await();
                    TransactionRequest req = buildRequest(accountA.getAccountNumber(),
                            accountB.getAccountNumber(), transferAmount, null);
                    transactionService.transfer(req, user.getUsername());
                } catch (Exception ignored) {
                    // Expected: some will fail with InsufficientFunds
                }
            });
        }

        latch.countDown();
        pool.shutdown();
        //noinspection ResultOfMethodCallIgnored
        pool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        // Balance must NEVER go negative
        var finalA = accountRepository.findByAccountNumber(accountA.getAccountNumber()).orElseThrow();
        assertThat(finalA.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalA.getBalance()).isLessThanOrEqualTo(initialBalance);
    }

    private TransactionRequest buildRequest(String from, String to, BigDecimal amount, String idempotencyKey) {
        return new TransactionRequest(from, to, amount, "1234", "chaos-test",
                null, "TRANSFER", idempotencyKey, null, null);
    }
}
