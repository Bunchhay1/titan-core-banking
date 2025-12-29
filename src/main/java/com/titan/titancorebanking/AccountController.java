package com.titan.titancorebanking;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;




@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    // 1. API បើកគណនីថ្មីឱ្យ User
    // POST http://localhost:8080/api/accounts?userId=1
    @PostMapping
    public Account createAccount(@RequestParam Long userId) {
        // រកមើល User តាមរយៈ ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        // បង្កើត Account ថ្មី
        Account account = new Account();
        account.setAccountNumber("001" + System.currentTimeMillis()); // បង្កើតលេខគណនីដោយចៃដន្យ
        account.setBalance(BigDecimal.valueOf(0.00)); // បើកដំបូងលុយ $0.00
        account.setUser(user); // ភ្ជាប់ Account នេះទៅ User ដែលរកឃើញ

        // Save ចូល Database
        return accountRepository.save(account);
    }

    // 2. API សម្រាប់ដាក់លុយចូល (Deposit) [NEW FEATURE]
    // POST http://localhost:8080/api/accounts/1/deposit?amount=100
    @PostMapping("/{accountId}/deposit")
    public Account deposit(@PathVariable Long accountId, @RequestParam BigDecimal amount) {
        // រកមើល Account តាមរយៈ ID
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        // បូកលុយចូល (Current Balance + New Amount)
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);

        // Save ទុកក្នុង Database
        return accountRepository.save(account);

    }

    // 3. API ផ្ទេរលុយ (Clean Version)
    @PostMapping("/transfer")
    public String transferMoney(@RequestParam Long fromId, @RequestParam Long toId, @RequestParam BigDecimal amount) {
        // ហៅ Service ឱ្យធ្វើការជំនួស
        accountService.transferMoney(fromId, toId, amount);

        return "✅ Transfer Success! Sent $" + amount;
    }
    // 4. API មើលប្រវត្តិប្រតិបត្តិការ (Bank Statement)
    // GET http://localhost:8080/api/accounts/1/transactions
    @GetMapping("/{accountId}/transactions")
    public java.util.List<Transaction> getStatement(@PathVariable Long accountId) {
        return transactionRepository.findByFromAccountIdOrToAccountId(accountId, accountId);
    }
}