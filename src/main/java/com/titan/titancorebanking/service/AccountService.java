package com.titan.titancorebanking.service;

import com.titan.titancorebanking.dto.request.AccountRequest;
import com.titan.titancorebanking.dto.request.TransactionRequest;
// 👇 (New Import) សម្រាប់បង្ហាញទិន្នន័យ
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.entity.AccountType;
import com.titan.titancorebanking.entity.*;
import com.titan.titancorebanking.enums.TransactionStatus;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import com.titan.titancorebanking.repository.UserRepository;
import com.titan.titancorebanking.utils.AccountNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
// 👇 (New Import) សម្រាប់ Pagination
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.titan.titancorebanking.enums.AccountStatus;
import com.titan.titancorebanking.entity.Account;
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

    // 🔑 Security Constants
    private static final String PIN_ATTEMPT_PREFIX = "PIN:ATTEMPTS:";
    private static final String PIN_LOCK_PREFIX = "PIN:LOCKED:";
    private static final int MAX_ACCOUNTS = 1;
    private static final BigDecimal HIGH_VALUE_LIMIT = new BigDecimal("100000"); // $100,000 Limit

    // ==========================================================
    // 1. ACCOUNT MANAGEMENT
    // ==========================================================

    @Transactional(readOnly = true)
    @Cacheable(value = "user_accounts", key = "#username")
    public List<Account> getMyAccounts(String username) {
        return accountRepository.findByUserUsername(username);
    }

    // ✅ NEW METHOD: BANK STATEMENT (Pagination) 📜
    // នៅក្នុង AccountService.java

    // នៅក្នុង AccountService.java

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAccountStatement(String accountNumber, int page, int size, String username) {

        // 1. Security Check
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getUsername().equals(username)) {
            throw new SecurityException("⛔ You are not the owner of this account!");
        }

        // 2. Pagination
        Pageable pageable = PageRequest.of(page, size);

        // 3. Query
        Page<Transaction> transactions = transactionRepository.findAllByAccountNumber(accountNumber, pageable);

        // 4. Mapping (កែឱ្យត្រូវនឹង DTO ចាស់របស់អ្នក) ✅
        return transactions.map(tx -> TransactionResponse.builder()
                .id(tx.getId())  // 👈 កែពី .transactionId(...) មក .id(...) វិញ
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .status(tx.getStatus() != null ? tx.getStatus().name() : "UNKNOWN")
                .note(tx.getNote())
                .timestamp(tx.getTimestamp())

                // .status(...) ❌ កុំដាក់ព្រោះ DTO អ្នកអត់មាន field status

                // Mapping Account Number
                .fromAccountNumber(tx.getFromAccount() != null ? tx.getFromAccount().getAccountNumber() : "N/A")
                .toAccountNumber(tx.getToAccount() != null ? tx.getToAccount().getAccountNumber() : "N/A")

                // Mapping Owner Name
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

                // ប្រយ័ត្ន: ត្រូវប្រាកដថា AccountType ជា Enum ត្រឹមត្រូវ
                .accountType(AccountType.valueOf(request.getAccountType()))

                // 💰 FIX: ប្រើ getInitialDeposit ជំនួសឱ្យ getBalance
                .balance(request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO)

                .user(user)
                .createdAt(LocalDateTime.now())

                // ✨ ADDED: កុំភ្លេចកំណត់របស់សំខាន់ ២ នេះ!
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

        // A. INITIALIZE (PENDING STATE)
        Transaction tx = Transaction.builder()
                .type(TransactionType.TRANSFER) // ✅ FIXED: ប្រើ .type() និង Enum
                .amount(request.getAmount())
                .timestamp(LocalDateTime.now())
                .status(TransactionStatus.PENDING)
                .note("Transaction Initiated")
                .build();

        tx = transactionRepository.save(tx);

        try {
            // B. LOAD USER & SECURITY CHECKS
            tx.setStatus(TransactionStatus.PROCESSING);

            User currentUser = userRepository.findByUsername(currentUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 🔒 RULE 1: HARD LOCK CHECK (DB Lock)
            if (!currentUser.isAccountNonLocked()) {
                throw new SecurityException("⛔ ACCOUNT LOCKED: Please contact the bank to unlock.");
            }

            // ⏳ RULE 2: TEMP LOCK CHECK (Redis Lock - 5 Minutes)
            if (Boolean.TRUE.equals(redisTemplate.hasKey(PIN_LOCK_PREFIX + currentUsername))) {
                throw new SecurityException("⏳ Too many wrong attempts. Account paused for 5 minutes.");
            }

            // 💰 RULE 3: HIGH VALUE CHECK ($100k Rule)
            if (request.getAmount().compareTo(HIGH_VALUE_LIMIT) >= 0) {
                if (request.getOtp() == null || request.getOtp().trim().isEmpty()) {
                    throw new IllegalArgumentException("🛡️ High Value Transfer ($100k+) requires OTP!");
                }
                otpService.validateOtp(currentUsername, request.getOtp());
            }

            // 🔢 RULE 4: PIN VERIFICATION
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

            // ✅ Set Relationships correctly
            tx.setFromAccount(fromAccount);
            tx.setToAccount(toAccount);

            if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
                throw new IllegalArgumentException("Insufficient Balance!");
            }

            // D. EXECUTION (MOVE MONEY) 💸
            fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
            toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            // E. SUCCESS STATE ✅
            tx.setStatus(TransactionStatus.SUCCESS);
            tx.setNote(request.getNote() != null ? request.getNote() : "Transfer Completed");

        } catch (Exception e) {
            // F. FAILURE HANDLING ❌
            tx.setStatus(TransactionStatus.FAILED);
            tx.setNote("Failure: " + e.getMessage());
            throw e;
        }

        return transactionRepository.save(tx);
    }

    // ==========================================================
    // 🔐 PRIVATE SECURITY HELPERS (LOCKING LOGIC)
    // ==========================================================

    private void handlePinFailure(String username, User user) {
        String key = PIN_ATTEMPT_PREFIX + username;

        // 1. Increment Counter
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, Duration.ofDays(1)); // Reset count every day
        }

        // 2. Temp Lock (5 Attempts -> Lock 5 Minutes)
        if (attempts != null && attempts == 5) {
            redisTemplate.opsForValue().set(PIN_LOCK_PREFIX + username, "LOCKED", Duration.ofMinutes(5));
        }

        // 3. Hard Lock (7 Attempts -> Database Lock)
        if (attempts != null && attempts >= 7) {
            user.setAccountNonLocked(false); // 🔒 Lock in DB
            userRepository.save(user);
            redisTemplate.delete(key); // Cleanup Redis
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