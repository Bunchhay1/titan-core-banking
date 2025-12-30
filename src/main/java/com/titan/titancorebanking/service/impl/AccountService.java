package com.titan.titancorebanking.service.impl;

import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service // ប្រាប់ Spring ថា Class នេះគឺជា Business Logic
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // មុខងារផ្ទេរលុយ (Logic សុទ្ធ)
    @Transactional // ធានាថាបើមាន Error គឺ Rollback លុយវិញទាំងអស់
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        // 1. SECURITY CHECK: ហាមផ្ទេរលុយអវិជ្ជមាន (ឧ. -100)
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be positive!");
        }

        // 2. SECURITY CHECK: ហាមផ្ទេរឱ្យខ្លួនឯង
        if (fromId.equals(toId)) {
            throw new RuntimeException("Cannot transfer to the same account!");
        }

        // 3. រកគណនី
        Account fromAccount = accountRepository.findById(fromId)
                .orElseThrow(() -> new RuntimeException("Sender account not found!"));
        Account toAccount = accountRepository.findById(toId)
                .orElseThrow(() -> new RuntimeException("Receiver account not found!"));

        // 4. ឆែកលុយ
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient Balance! You only have: $" + fromAccount.getBalance());
        }

        // 5. ធ្វើប្រតិបត្តិការ (ដក និង ដាក់)
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // 6. កត់ត្រាប្រវត្តិ
        Transaction tx = new Transaction();
        tx.setType("TRANSFER");
        tx.setAmount(amount);
        tx.setFromAccountId(fromId);
        tx.setToAccountId(toId);
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);
    }
}