package com.titan.titancorebanking.controller;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.TransactionResponse;
import com.titan.titancorebanking.entity.Transaction;
import com.titan.titancorebanking.service.AccountService;
import com.titan.titancorebanking.service.TransactionService;

// ✅ Swagger Imports
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction Management", description = "Operations for Transfers, Deposits, and Withdrawals") // ✅ Group Name
public class TransactionController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    // ==========================================
    // 💸 1. TRANSFER ENDPOINT
    // ==========================================
    @Operation(summary = "Transfer Money", description = "Transfer funds between two accounts. Triggers AI Risk Check.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer Successful"),
            @ApiResponse(responseCode = "400", description = "Insufficient Funds or Validation Error"),
            @ApiResponse(responseCode = "403", description = "Account Locked or High Risk Blocked")
    })
    @PostMapping("/transfer")
    public ResponseEntity<Transaction> transferMoney(
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication
    ) {
        Transaction tx = transactionService.transfer(request, authentication.getName());
        return ResponseEntity.ok(tx);
    }

    // ==========================================
    // 📜 2. HISTORY ENDPOINT
    // ==========================================
    @Operation(summary = "Get Transaction History", description = "Retrieve recent transactions for the logged-in user.")
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getMyTransactions(Authentication authentication) {
        return ResponseEntity.ok(transactionService.getMyTransactions(authentication.getName()));
    }

    // ==========================================
    // 💰 3. DEPOSIT ENDPOINT
    // ==========================================
    @Operation(summary = "Deposit Cash", description = "Add funds to an account (Branch Operation).")
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody TransactionRequest request) {
        transactionService.deposit(request);
        return ResponseEntity.ok(Map.of("message", "💰 Deposit Successful!"));
    }

    // ==========================================
    // 🏧 4. WITHDRAW ENDPOINT
    // ==========================================
    @Operation(summary = "Withdraw Cash", description = "Withdraw funds from an ATM.")
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody TransactionRequest request, Authentication authentication) {
        transactionService.withdraw(request, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "💸 Withdrawal Successful!"));
    }

    // ==========================================
    // 📊 5. STATEMENT ENDPOINT
    // ==========================================
    @Operation(summary = "Get Account Statement", description = "Paginated list of transactions for a specific account.")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<Page<TransactionResponse>> getStatement(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(accountService.getAccountStatement(accountNumber, page, size, authentication.getName()));
    }
}