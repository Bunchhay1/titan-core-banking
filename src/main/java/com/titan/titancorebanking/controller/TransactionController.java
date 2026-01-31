package com.titan.titancorebanking.controller;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.enums.TransactionStatus;
import com.titan.titancorebanking.service.AccountService;
import com.titan.titancorebanking.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map; // ✅ ថែម Map ដើម្បី return JSON សាមញ្ញ

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    // ==========================================
    // 💸 1. TRANSFER ENDPOINT
    // ==========================================
    @PostMapping("/transfer")
    // 1. បន្ថែម @Valid នៅទីនេះ ដើម្បីឱ្យ Validation ដំណើរការ
    public ResponseEntity<Transaction> transferMoney(
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication
    ) {
        // 2. ហៅ Service (បើមាន Error, វានឹងលោតទៅ Exception Handler ភ្លាម)
        Transaction tx = accountService.transferMoney(request, authentication.getName());

        // 3. មិនបាច់ Check status ទេ! បើមកដល់ទីនេះ គឺជោគជ័យហើយ។
        return ResponseEntity.ok(tx);
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
        // ✅ Return ជា JSON: { "message": "..." }
        return ResponseEntity.ok(Map.of("message", "💰 Deposit Successful!"));
    }

    // ==========================================
    // 🏧 4. WITHDRAW ENDPOINT
    // ==========================================
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody TransactionRequest request, Authentication authentication) {
        transactionService.withdraw(request, authentication.getName());
        // ✅ Return ជា JSON
        return ResponseEntity.ok(Map.of("message", "💸 Withdrawal Successful!"));
    }

    // ==========================================
    // 📊 5. STATEMENT ENDPOINT
    // ==========================================
    @GetMapping("/{accountNumber}")
    public ResponseEntity<Page<TransactionResponse>> getStatement(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication // ✅ Inject ផ្ទាល់ មិនបាច់ប្រើ SecurityContextHolder
    ) {
        return ResponseEntity.ok(accountService.getAccountStatement(accountNumber, page, size, authentication.getName()));
    }
}