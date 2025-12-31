package com.titan.titancorebanking.service;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.entity.TransactionType;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service // ✅ ប្រាប់ Spring ថា Class នេះផ្ទុក Business Logic
@RequiredArgsConstructor // ✅ បង្កើត Constructor ដោយស្វ័យប្រវត្តិ (Dependency Injection)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================================================================================
    // 💸 1. TRANSFER MONEY (វេរលុយពីគណនីមួយ ទៅគណនីមួយ)
    // ==================================================================================
    // 🔍 @Transactional: ធានាថាប្រតិបត្តិការនេះគឺ "All or Nothing" (ACID Principle).
    // បើមាន Error នៅត្រង់ណាមួយ (ឧ. កាត់លុយបាន តែដាក់លុយមិនចូល) វានឹង Rollback ត្រឡប់ដើមវិញទាំងអស់។
    @Transactional
    public void transfer(TransactionRequest request, String currentUsername) {

        // 🟢 Step 1: ស្វែងរកគណនីអ្នកផ្ញើ (Source Account)
        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found: " + request.getFromAccountNumber()));

        // 🛡️ Step 2: SECURITY CHECK (Authorization)
        // ពិនិត្យថា តើអ្នកដែលកំពុង Login (Token) ពិតជាម្ចាស់គណនីនេះមែនឬអត់?
        // ការពារករណី Hacker យក Token ខ្លួនឯង ទៅវេរលុយចេញពីកុងអ្នកដទៃ (IDOR Attack).
        if (!fromAccount.getUser().getUsername().equals(currentUsername)) {
            throw new RuntimeException("⛔ You are not the owner of account: " + request.getFromAccountNumber());
        }

        // 🛡️ Step 3: PIN VALIDATION
        // ប្រើ passwordEncoder.matches() ដើម្បីផ្ទៀងផ្ទាត់ Hash នៃ PIN.
        // ហាមដាច់ខាតយក String មក compare គ្នាត្រង់ៗ (Security Risk).
        if (!passwordEncoder.matches(request.getPin(), fromAccount.getUser().getPin())) {
            throw new RuntimeException("❌ Invalid PIN");
        }

        // 💰 Step 4: BALANCE CHECK
        // ពិនិត្យមើលថាមានលុយគ្រប់គ្រាន់ទេ? (ប្រើ compareTo សម្រាប់ BigDecimal)
        // fromAccount < requestAmount
        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("❌ Insufficient balance");
        }

        // 🟢 Step 5: ស្វែងរកគណនីអ្នកទទួល (Target Account)
        Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("Receiver Account not found: " + request.getToAccountNumber()));

        // ⚡ Step 6: EXECUTE TRANSFER (ប្រតិបត្តិការដក និង ដាក់)
        // កាត់លុយពីអ្នកផ្ញើ
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        // ដាក់លុយឱ្យអ្នកទទួល
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        // Save បម្រែបម្រួលចូល Database
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // 📝 Step 7: AUDIT LOG (កត់ត្រាប្រវត្តិ)
        // សំខាន់ណាស់សម្រាប់ការធ្វើរបាយការណ៍ និងដោះស្រាយបញ្ហាពេលក្រោយ
        Transaction transaction = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .timestamp(LocalDateTime.now())
                .note(request.getNote())
                .build();

        transactionRepository.save(transaction);
    }

    // ==================================================================================
    // 💰 2. DEPOSIT (ដាក់លុយចូលគណនី - Cash In)
    // ==================================================================================
    @Transactional
    public void deposit(TransactionRequest request) {
        // 🟢 Step 1: រកគណនីដែលត្រូវដាក់លុយចូល
        Account account = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // ⚡ Step 2: ដាក់លុយចូល (Add Balance)
        // ចំណាំ: Deposit មិនត្រូវការ PIN ទេ ព្រោះជាការដាក់លុយចូល (អាចធ្វើនៅបញ្ជរ)
        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        // 📝 Step 3: កត់ត្រាប្រវត្តិ
        Transaction transaction = Transaction.builder()
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .toAccount(account) // ដាក់ account ចូលទៅក្នុង Field 'toAccount'
                .timestamp(LocalDateTime.now())
                .note("Cash Deposit at Branch 🏦")
                .build();

        transactionRepository.save(transaction);
    }

    // ==================================================================================
    // 🏧 3. WITHDRAWAL (ដកលុយសុទ្ធ - Cash Out)
    // ==================================================================================
    @Transactional
    public void withdraw(TransactionRequest request, String currentUsername) {
        // 🟢 Step 1: រកគណនីដែលត្រូវដកលុយ
        Account account = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // 🛡️ Step 2: SECURITY CHECK (ដូច Transfer ដែរ)
        if (!account.getUser().getUsername().equals(currentUsername)) {
            throw new RuntimeException("⛔ You are not the owner of this account!");
        }

        // 🛡️ Step 3: PIN CHECK
        if (!passwordEncoder.matches(request.getPin(), account.getUser().getPin())) {
            throw new RuntimeException("❌ Invalid PIN");
        }

        // 💰 Step 4: BALANCE CHECK
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("❌ Insufficient balance");
        }

        // ⚡ Step 5: កាត់លុយចេញ (Subtract Balance)
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        // 📝 Step 6: កត់ត្រាប្រវត្តិ
        Transaction transaction = Transaction.builder()
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .fromAccount(account) // ដាក់ account ចូលទៅក្នុង Field 'fromAccount'
                .timestamp(LocalDateTime.now())
                .note("Cash Withdrawal via ATM 🏧")
                .build();

        transactionRepository.save(transaction);
    }

    // ==================================================================================
    // 📜 4. VIEW HISTORY (មើលប្រវត្តិប្រតិបត្តិការ)
    // ==================================================================================
    public List<TransactionResponse> getMyTransactions(String username) {
        // 🟢 Step 1: ហៅ SQL Query ពី Repository ដើម្បីយក Transaction ទាំងអស់របស់ User នេះ
        List<Transaction> transactions = transactionRepository.findAllByUser(username);

        // 🔄 Step 2: Data Transformation (Entity -> DTO)
        // យើងមិនបញ្ជូន Entity ផ្ទាល់ទៅ Frontend ទេ ដើម្បីលាក់ទិន្នន័យរសើប និងសម្រួលទម្រង់ JSON
        return transactions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // 🛠️ Helper Method: សម្រាប់បំប្លែងទិន្នន័យ
    private TransactionResponse mapToResponse(Transaction tx) {
        return TransactionResponse.builder().id(tx.getId())
                .type(tx.getType().toString())
                .amount(tx.getAmount())
                .note(tx.getNote())
                .timestamp(tx.getTimestamp())

                // ✅ Safe Null Check:
                // ប្រសិនបើជា Deposit, fromAccount នឹង null.
                // ប្រសិនបើជា Withdraw, toAccount នឹង null.
                // យើងប្រើ Ternary Operator (? :) ដើម្បីការពារ NullPointerException
                .fromAccountNumber(tx.getFromAccount() != null ? tx.getFromAccount().getAccountNumber() : null)
                .toAccountNumber(tx.getToAccount() != null ? tx.getToAccount().getAccountNumber() : null)

                // ✅ បង្ហាញឈ្មោះម្ចាស់គណនី (Full Name Logic)
                .fromOwnerName(tx.getFromAccount() != null ? tx.getFromAccount().getUser().getFullName() : null)
                .toOwnerName(tx.getToAccount() != null ? tx.getToAccount().getUser().getFullName() : null)
                .build();
    }
}