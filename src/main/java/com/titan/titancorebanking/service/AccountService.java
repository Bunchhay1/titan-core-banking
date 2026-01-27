package com.titan.titancorebanking.service;

import com.titan.titancorebanking.dto.request.AccountRequest;
import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.entity.*;
import com.titan.titancorebanking.enums.TransactionStatus;
import com.titan.titancorebanking.enums.AccountStatus;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import com.titan.titancorebanking.repository.UserRepository;
import com.titan.titancorebanking.utils.AccountNumberUtils;
import com.titan.titancorebanking.exception.InsufficientBalanceException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final StringRedisTemplate redisTemplate;

    // ✅ 1. Inject AI Service
    private final TitanAiService titanAiService;

    // 🔑 Security Constants
    private static final String PIN_ATTEMPT_PREFIX = "PIN:ATTEMPTS:";
    private static final String PIN_LOCK_PREFIX = "PIN:LOCKED:";
    private static final int MAX_ACCOUNTS = 1;
    private static final BigDecimal HIGH_VALUE_LIMIT = new BigDecimal("100000");

    // ==========================================================
    // 1. ACCOUNT MANAGEMENT
    // ==========================================================

    @Transactional(readOnly = true)
    @Cacheable(value = "user_accounts", key = "#username")
    public List<Account> getMyAccounts(String username) {
        return accountRepository.findByUserUsername(username);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAccountStatement(String accountNumber, int page, int size, String username) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getUsername().equals(username)) {
            throw new SecurityException("⛔ You are not the owner of this account!");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> transactions = transactionRepository.findAllByAccountNumber(accountNumber, pageable);

        return transactions.map(tx -> TransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .status(tx.getStatus() != null ? tx.getStatus().name() : "UNKNOWN")
                .note(tx.getNote())
                .timestamp(tx.getTimestamp())
                .fromAccountNumber(tx.getFromAccount() != null ? tx.getFromAccount().getAccountNumber() : "N/A")
                .toAccountNumber(tx.getToAccount() != null ? tx.getToAccount().getAccountNumber() : "N/A")
                .fromOwnerName(tx.getFromAccount() != null ? tx.getFromAccount().getUser().getUsername() : "System")
                .toOwnerName(tx.getToAccount() != null ? tx.getToAccount().getUser().getUsername() : "External")
                .build());
    }

    @Transactional
    @CacheEvict(value = "user_accounts", key = "#username")
    public Account createAccount(AccountRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (accountRepository.countByUser(user) >= MAX_ACCOUNTS) {
            throw new RuntimeException("⛔ Limit Reached: You can only create " + MAX_ACCOUNTS + " account.");
        }

        Account account = Account.builder()
                .accountNumber(AccountNumberUtils.generateAccountNumber())
                .accountType(AccountType.valueOf(request.getAccountType()))
                .balance(request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO)
                .user(user)
                .createdAt(LocalDateTime.now())
                .status(AccountStatus.ACTIVE)
                .currency("USD")
                .build();

        return accountRepository.save(account);
    }

    // ==========================================================
    // 2. CORE BANKING LOGIC (SMART SECURITY) 🛡️🧠
    // ==========================================================
    @Transactional
    public Transaction transferMoney(TransactionRequest request, String currentUsername) {

        Transaction tx = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .timestamp(LocalDateTime.now())
                .status(TransactionStatus.PENDING)
                .note("Transaction Initiated")
                .build();

        tx = transactionRepository.save(tx);

        try {
            tx.setStatus(TransactionStatus.PROCESSING);
            User currentUser = userRepository.findByUsername(currentUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 🔒 RULE 1: HARD LOCK CHECK
            if (!currentUser.isAccountNonLocked()) {
                throw new SecurityException("⛔ ACCOUNT LOCKED: Please contact the bank to unlock.");
            }

            // ⏳ RULE 2: TEMP LOCK CHECK
            if (Boolean.TRUE.equals(redisTemplate.hasKey(PIN_LOCK_PREFIX + currentUsername))) {
                throw new SecurityException("⏳ Too many wrong attempts. Account paused for 5 minutes.");
            }

            // 🧠 RULE 3: AI SECURITY CHECK (NEW!) 🛡️
            // ហៅទៅ Python AI នៅត្រង់នេះ!
            titanAiService.analyzeTransaction(currentUsername, request.getAmount());

            // 💰 RULE 4: HIGH VALUE CHECK ($100k+)
            if (request.getAmount().compareTo(HIGH_VALUE_LIMIT) >= 0) {
                if (request.getOtp() == null || request.getOtp().trim().isEmpty()) {
                    throw new IllegalArgumentException("🛡️ High Value Transfer ($100k+) requires OTP!");
                }
                otpService.validateOtp(currentUsername, request.getOtp());
            }

            // 🔢 RULE 5: PIN VERIFICATION
            Account fromAccount = accountRepository.findByAccountNumberForUpdate(request.getFromAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Sender account not found"));

            if (!fromAccount.getUser().getUsername().equals(currentUsername)) {
                throw new SecurityException("⛔ You do not own this sender account!");
            }

            if (!passwordEncoder.matches(request.getPin(), currentUser.getPin())) {
                handlePinFailure(currentUsername, currentUser);
                throw new SecurityException("❌ Incorrect PIN!");
            }

            resetPinAttempts(currentUsername);

            // C. STANDARD BUSINESS LOGIC
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive!");
            }

            Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Receiver account not found"));

            tx.setFromAccount(fromAccount);
            tx.setToAccount(toAccount);

            if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException("Insufficient Balance! Your current balance is $" + fromAccount.getBalance());
            }

            // D. EXECUTION (MOVE MONEY) 💸
            fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
            toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            tx.setStatus(TransactionStatus.SUCCESS);
            tx.setNote(request.getNote() != null ? request.getNote() : "Transfer Completed");

        } catch (Exception e) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setNote("Failure: " + e.getMessage());
            throw e;
        }

        return transactionRepository.save(tx);
    }

    // ==========================================================
    // 🔐 PRIVATE SECURITY HELPERS
    // ==========================================================

    private void handlePinFailure(String username, User user) {
        String key = PIN_ATTEMPT_PREFIX + username;
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        if (attempts != null && attempts == 5) {
            redisTemplate.opsForValue().set(PIN_LOCK_PREFIX + username, "LOCKED", Duration.ofMinutes(5));
        }
        if (attempts != null && attempts >= 7) {
            user.setAccountNonLocked(false);
            userRepository.save(user);
            redisTemplate.delete(key);
        }
    }

    private void resetPinAttempts(String username) {
        redisTemplate.delete(PIN_ATTEMPT_PREFIX + username);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return account.getBalance();
    }
}