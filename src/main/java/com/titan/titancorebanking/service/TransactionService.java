package com.titan.titancorebanking.service;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.entity.TransactionType;
import com.titan.titancorebanking.enums.TransactionStatus;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;

// ✅ Events & AI
import org.springframework.context.ApplicationEventPublisher;
import com.titan.titancorebanking.event.TransactionEvent;
import com.titan.riskengine.RiskCheckResponse;

// ✅ Cache
import org.springframework.cache.annotation.CacheEvict;

// ✅ Audit Log Annotation
import com.titan.titancorebanking.annotation.AuditLog;

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
    private final ApplicationEventPublisher eventPublisher;
    private final RiskEngineGrpcService riskEngineGrpcService;

    // ==================================================================================
    // 💸 1. TRANSFER MONEY
    // ==================================================================================
    @Transactional
    @CacheEvict(value = "user_accounts", allEntries = true)
    @AuditLog(action = "TRANSFER_MONEY") // 📼 AUDIT RECORDING ENABLED
    public Transaction transfer(TransactionRequest request, String currentUsername) {

        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!fromAccount.getUser().getUsername().equals(currentUsername)) {
            throw new RuntimeException("⛔ You are not the owner of this account");
        }
        if (!passwordEncoder.matches(request.getPin(), fromAccount.getUser().getPin())) {
            throw new RuntimeException("❌ Invalid PIN");
        }

        // 🤖 AI CHECK
        logger.info("🔍 Asking Python AI (gRPC) for user: {}", currentUsername);
        RiskCheckResponse risk = null;
        try {
            risk = riskEngineGrpcService.analyzeTransaction(currentUsername, request.getAmount().doubleValue());
        } catch (Exception e) {
            logger.error("⚠️ AI Service Unavailable: {}", e.getMessage());
        }

        if (risk != null && "BLOCK".equalsIgnoreCase(risk.getAction())) {
            throw new RuntimeException("🚨 Transaction BLOCKED by AI!");
        }

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("❌ Insufficient balance");
        }

        Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("Receiver Account not found"));

        // UPDATE BALANCE
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .timestamp(LocalDateTime.now())
                .status(TransactionStatus.SUCCESS)
                .note(request.getNote())
                .build();

        Transaction savedTx = transactionRepository.save(transaction);

        // 🚀 EVENT PUBLISH
        String successMsg = "✅ Transfer Successful! Sent $" + request.getAmount() + " to " + request.getToAccountNumber();
        eventPublisher.publishEvent(new TransactionEvent(currentUsername, request.getAmount(), "TRANSFER", successMsg));

        return savedTx;
    }

    // ==================================================================================
    // 💰 2. DEPOSIT
    // ==================================================================================
    @Transactional
    @CacheEvict(value = "user_accounts", allEntries = true)
    @AuditLog(action = "CASH_DEPOSIT") // 📼 AUDIT RECORDING ENABLED
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
                .status(TransactionStatus.SUCCESS)
                .note("Cash Deposit")
                .build();

        transactionRepository.save(transaction);

        String username = account.getUser().getUsername();
        String msg = "💰 Deposit Successful: +$" + request.getAmount();
        eventPublisher.publishEvent(new TransactionEvent(username, request.getAmount(), "DEPOSIT", msg));
    }

    // ==================================================================================
    // 🏧 3. WITHDRAWAL
    // ==================================================================================
    @Transactional
    @CacheEvict(value = "user_accounts", allEntries = true)
    @AuditLog(action = "CASH_WITHDRAWAL") // 📼 AUDIT RECORDING ENABLED
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
                .status(TransactionStatus.SUCCESS)
                .note("ATM Withdrawal")
                .build();

        transactionRepository.save(transaction);

        String msg = "🏧 Withdrawal Successful: -$" + request.getAmount();
        eventPublisher.publishEvent(new TransactionEvent(currentUsername, request.getAmount(), "WITHDRAWAL", msg));
    }

    // ... (getMyTransactions unchanged) ...
    public List<TransactionResponse> getMyTransactions(String username) {
        List<Transaction> transactions = transactionRepository.findAllByUser(username);
        return transactions.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
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