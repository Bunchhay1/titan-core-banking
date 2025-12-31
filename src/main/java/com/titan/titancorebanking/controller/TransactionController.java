package com.titan.titancorebanking.controller;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.enums.TransactionStatus;
import com.titan.titancorebanking.service.AccountService; // ✅ 1. ត្រូវមាន Import នេះ
import com.titan.titancorebanking.service.TransactionService;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    // 👇 2. ត្រូវប្រកាស AccountService នៅទីនេះ (Dependency Injection)
    private final AccountService accountService;

    // Service ចាស់សម្រាប់ History/Deposit
    private final TransactionService transactionService;

    // ==========================================
    // 💸 1. TRANSFER ENDPOINT (Logic ថ្មី)
    // ==========================================
    @PostMapping("/transfer")
    public ResponseEntity<?> transferMoney(
            @RequestBody TransactionRequest request,
            Authentication authentication
    ) {
        // 3. ហៅ accountService.transferMoney (មិនមែន transactionService.transfer ទេ)
        Transaction tx = accountService.transferMoney(request, authentication.getName());

        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            return ResponseEntity.ok(tx);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(tx);
        }
    }

    // ==========================================
    // 📜 2. HISTORY ENDPOINT
    // ==========================================
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getMyTransactions(Authentication authentication) {
        return ResponseEntity.ok(transactionService.getMyTransactions(authentication.getName()));
    }

    // ==========================================
    // 💰 3. DEPOSIT ENDPOINT
    // ==========================================
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody TransactionRequest request) {
        transactionService.deposit(request);
        return ResponseEntity.ok("💰 Deposit Successful!");
    }

    // ==========================================
    // 🏧 4. WITHDRAW ENDPOINT
    // ==========================================
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody TransactionRequest request, Authentication authentication) {
        transactionService.withdraw(request, authentication.getName());
        return ResponseEntity.ok("💸 Withdrawal Successful!");
    }
    @GetMapping("/{accountNumber}")
    public ResponseEntity<Page<TransactionResponse>> getStatement(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(accountService.getAccountStatement(accountNumber, page, size, username));
    }
}