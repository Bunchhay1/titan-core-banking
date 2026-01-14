package com.titan.titancorebanking.service;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.dto.response.RiskCheckResponse; // ត្រូវប្រាកដថា DTO នេះមាន
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.entity.TransactionType;
import com.titan.titancorebanking.enums.TransactionStatus; // ✅ Import Status
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
// ✅ ត្រូវប្រាកដថា Import ពី Package 'imple' បើ service នោះនៅទីនោះ
// import com.titan.titancorebanking.service.imple.RiskEngineGrpcService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    // ✅ Service សម្រាប់ហៅទៅ Python AI
    private final RiskEngineGrpcService riskEngineGrpcService;

    // ==================================================================================
    // 💸 1. TRANSFER MONEY (វេរលុយ)
    // ==================================================================================
    @Transactional
    public void transfer(TransactionRequest request, String currentUsername) {

        // 1. រកគណនីអ្នកផ្ញើ
        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // 2. ផ្ទៀងផ្ទាត់ម្ចាស់គណនី
        if (!fromAccount.getUser().getUsername().equals(currentUsername)) {
            throw new RuntimeException("⛔ You are not the owner of this account");
        }
        if (!passwordEncoder.matches(request.getPin(), fromAccount.getUser().getPin())) {
            throw new RuntimeException("❌ Invalid PIN");
        }

        // ============================================================
        // 🤖 gRPC CHECK: ហៅទៅ AI តាមរយៈ gRPC
        // ============================================================
        logger.info("🔍 Asking Python AI (gRPC) for user: {}", currentUsername);

        try {
            RiskCheckResponse risk = riskEngineGrpcService.analyzeTransaction(currentUsername, request.getAmount());

            if ("BLOCK".equalsIgnoreCase(risk.action())) {
                throw new RuntimeException("🚨 Transaction BLOCKED by AI!");
            }
        } catch (Exception e) {
            // បើ AI ដាច់ (Connection Refused) តើយើងគួរ Block ឬ Allow?
            // សម្រាប់សុវត្ថិភាពខ្ពស់៖ Block. សម្រាប់ការរកស៊ី៖ Allow (Log Error).
            logger.error("⚠️ AI Service Unavailable: {}", e.getMessage());
            // throw new RuntimeException("AI System Down, please try again later."); // Uncomment បើចង់តឹងរ៉ឹង
        }
        // ============================================================

        // 4. ពិនិត្យសមតុល្យ (Balance Check)
        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("❌ Insufficient balance");
        }

        // 5. រកគណនីអ្នកទទួល
        Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("Receiver Account not found"));

        // 6. ធ្វើប្រតិបត្តិការ (Update Balance)
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // 7. កត់ត្រា (Audit Log)
        Transaction transaction = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .timestamp(LocalDateTime.now())

                // ✅✅✅ ដាក់ STATUS (កុំឱ្យ Error ដូច Deposit មុននេះ)
                .status(TransactionStatus.SUCCESS)

                .note(request.getNote())
                .build();

        transactionRepository.save(transaction);

        // 📢 NOTIFICATION
        String successMsg = "✅ Transfer Successful! You sent $" + request.getAmount() + " to " + request.getToAccountNumber();
        notificationService.sendNotification(currentUsername, successMsg);
    }

    // ==================================================================================
    // 💰 2. DEPOSIT
    // ==================================================================================
    @Transactional
    public void deposit(TransactionRequest request) {
        Account account = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .toAccount(account)
                .timestamp(LocalDateTime.now())
                .status(TransactionStatus.SUCCESS) // ✅ មានហើយ (ល្អ)
                .note("Cash Deposit at Branch 🏦")
                .build();

        transactionRepository.save(transaction);
    }

    // ==================================================================================
    // 🏧 3. WITHDRAWAL
    // ==================================================================================
    @Transactional
    public void withdraw(TransactionRequest request, String currentUsername) {
        Account account = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getUsername().equals(currentUsername)) {
            throw new RuntimeException("⛔ You are not the owner of this account!");
        }

        if (!passwordEncoder.matches(request.getPin(), account.getUser().getPin())) {
            throw new RuntimeException("❌ Invalid PIN");
        }

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("❌ Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .fromAccount(account)
                .timestamp(LocalDateTime.now())

                // ✅✅✅ ដាក់ STATUS ផង! (អ្នកភ្លេចត្រង់នេះ)
                .status(TransactionStatus.SUCCESS)

                .note("Cash Withdrawal via ATM 🏧")
                .build();

        transactionRepository.save(transaction);

        notificationService.sendNotification(currentUsername, "🏧 Cash Withdrawal: $" + request.getAmount());
    }

    // ... (View History code នៅដដែល) ...
    public List<TransactionResponse> getMyTransactions(String username) {
        List<Transaction> transactions = transactionRepository.findAllByUser(username);
        return transactions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        return TransactionResponse.builder().id(tx.getId())
                .type(tx.getType().toString())
                .amount(tx.getAmount())
                .note(tx.getNote())
                .timestamp(tx.getTimestamp())
                .fromAccountNumber(tx.getFromAccount() != null ? tx.getFromAccount().getAccountNumber() : null)
                .toAccountNumber(tx.getToAccount() != null ? tx.getToAccount().getAccountNumber() : null)
                .fromOwnerName(tx.getFromAccount() != null ? tx.getFromAccount().getUser().getFullName() : null)
                .toOwnerName(tx.getToAccount() != null ? tx.getToAccount().getUser().getFullName() : null)
                .build();
    }
}