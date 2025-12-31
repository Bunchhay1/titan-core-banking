package com.titan.titancorebanking.service;

import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.entity.TransactionType;
import com.titan.titancorebanking.enums.TransactionStatus; // 👈 1. Import Enum ថ្មី
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterestService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    // Run every 10 seconds (for demo)
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void calculateInterest() {
        List<Account> accounts = accountRepository.findAll();
        BigDecimal interestRate = new BigDecimal("0.005"); // 0.5%

        for (Account account : accounts) {
            if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal interest = account.getBalance().multiply(interestRate);

                // Update Balance
                account.setBalance(account.getBalance().add(interest));
                accountRepository.save(account);

                // Create Transaction Record
                Transaction tx = Transaction.builder()
                        .type(TransactionType.DEPOSIT) // ឬ INTEREST បើមាន
                        .amount(interest)
                        .toAccount(account)
                        .timestamp(LocalDateTime.now())

                        // 👇 2. បន្ថែមចំណុចនេះ (ដោះស្រាយ Error)
                        .status(TransactionStatus.SUCCESS)
                        .note("Monthly Interest Payment")

                        .build();

                transactionRepository.save(tx);

                log.info("💰 Interest Paid: {} to Account: {}", interest, account.getAccountNumber());
            }
        }
    }
}