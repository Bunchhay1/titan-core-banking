package com.titan.titancorebanking.integration;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.enums.AccountStatus;
import com.titan.titancorebanking.enums.AccountType;
import com.titan.titancorebanking.enums.Currency;
import com.titan.titancorebanking.model.Account;
import com.titan.titancorebanking.model.User;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.UserRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Testcontainers
@SpringBootTest
class TransferScenarioTest {

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
        registry.add("grpc.client.riskEngineClient.address", () -> "static://localhost:19999");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired TransactionService transactionService;
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User user;
    private Account usdAccount;
    private Account khrAccount;
    private Account eurAccount;

    record Scenario(String name, TransactionRequest request, String username, boolean expectSuccess) {}

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("transfer-test-" + UUID.randomUUID())
                .password(passwordEncoder.encode("secret"))
                .pin(passwordEncoder.encode("1234"))
                .role("ROLE_USER")
                .build());

        usdAccount = accountRepository.save(Account.builder()
                .accountNumber("USD-" + UUID.randomUUID().toString().substring(0, 8))
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("50000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user).build());

        khrAccount = accountRepository.save(Account.builder()
                .accountNumber("KHR-" + UUID.randomUUID().toString().substring(0, 8))
                .accountType(AccountType.SAVINGS)
                .currency(Currency.KHR)
                .balance(new BigDecimal("1000000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user).build());

        eurAccount = accountRepository.save(Account.builder()
                .accountNumber("EUR-" + UUID.randomUUID().toString().substring(0, 8))
                .accountType(AccountType.CHECKING)
                .currency(Currency.EUR)
                .balance(new BigDecimal("20000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user).build());
    }

    @Test
    void runAllTransferScenarios() {
        List<Scenario> scenarios = buildScenarios();
        int total = scenarios.size();
        int passed = 0, failed = 0;

        System.out.println("\n========================================");
        System.out.println("  💸 Transfer Scenario Test Suite");
        System.out.println("========================================\n");

        for (int i = 0; i < total; i++) {
            Scenario scenario = scenarios.get(i);
            String label = "[%d/%d] %s".formatted(i + 1, total, scenario.name());
            try {
                var tx = transactionService.transfer(scenario.request(), scenario.username());
                if (scenario.expectSuccess()) {
                    System.out.printf("✅ PASS: %s%n", label);
                    passed++;
                } else {
                    System.out.printf("❌ FAIL: %s — expected failure but got SUCCESS (txId=%s)%n", label, tx.getId());
                    failed++;
                }
            } catch (Exception e) {
                if (!scenario.expectSuccess()) {
                    System.out.printf("✅ PASS: %s — correctly rejected: %s%n", label, e.getMessage());
                    passed++;
                } else {
                    System.out.printf("❌ FAIL: %s — %s%n", label, e.getMessage());
                    failed++;
                }
            }
        }

        System.out.println("\n========================================");
        System.out.printf("  Results: %d passed, %d failed / %d total%n", passed, failed, total);
        System.out.println("========================================\n");

        if (failed > 0) {
            throw new AssertionError(failed + " scenario(s) failed. See output above.");
        }
    }

    private List<Scenario> buildScenarios() {
        List<Scenario> list = new ArrayList<>();

        // ── Same-currency transfers ──────────────────────────────────────────
        list.add(new Scenario("Local Transfer (USD → USD)",
                req(usdAccount, usdAccount, "100.00", "1234", null), user.getUsername(), false)); // same acc

        list.add(new Scenario("Local Transfer (USD → KHR, small amount)",
                req(usdAccount, khrAccount, "50.00", "1234", null), user.getUsername(), true));

        list.add(new Scenario("Local Transfer with FX Conversion (USD → KHR)",
                req(usdAccount, khrAccount, "200.00", "1234", "FX test"), user.getUsername(), true));

        list.add(new Scenario("Local Transfer with FX Conversion (USD → EUR)",
                req(usdAccount, eurAccount, "300.00", "1234", "USD to EUR"), user.getUsername(), true));

        list.add(new Scenario("Local Transfer with FX Conversion (EUR → KHR)",
                req(eurAccount, khrAccount, "100.00", "1234", "EUR to KHR"), user.getUsername(), true));

        list.add(new Scenario("Local Transfer with FX Conversion (KHR → USD)",
                req(khrAccount, usdAccount, "400000.00", "1234", "KHR to USD"), user.getUsername(), true));

        // ── Edge cases ───────────────────────────────────────────────────────
        list.add(new Scenario("Transfer with wrong PIN",
                req(usdAccount, khrAccount, "100.00", "0000", null), user.getUsername(), false));

        list.add(new Scenario("Transfer exceeding balance",
                req(usdAccount, khrAccount, "999999.00", "1234", null), user.getUsername(), false));

        list.add(new Scenario("Transfer minimum amount (0.01)",
                req(usdAccount, khrAccount, "0.01", "1234", "min amount"), user.getUsername(), true));

        list.add(new Scenario("Transfer with idempotency key",
                new TransactionRequest(
                        usdAccount.getAccountNumber(), khrAccount.getAccountNumber(),
                        new BigDecimal("75.00"), "1234", "idempotent",
                        null, "TRANSFER", "idem-key-" + UUID.randomUUID(), null, null),
                user.getUsername(), true));

        return list;
    }

    private TransactionRequest req(Account from, Account to, String amount, String pin, String note) {
        return new TransactionRequest(
                from.getAccountNumber(), to.getAccountNumber(),
                new BigDecimal(amount), pin, note,
                null, "TRANSFER", null, null, null);
    }
}
