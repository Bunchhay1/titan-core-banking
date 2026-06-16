package com.titan.titancorebanking.controller;

import com.titan.titancorebanking.model.Account;
import com.titan.titancorebanking.model.Loan;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.LoanRepository;
import com.titan.titancorebanking.service.LoanRepaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final LoanRepaymentService loanRepaymentService;

    @PostMapping("/apply")
    public ResponseEntity<?> apply(@RequestBody Map<String, Object> payload) {
        Long accountId = ((Number) payload.get("accountId")).longValue();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Loan loan = Loan.builder()
                .account(account)
                .userId(account.getUser().getId())
                .amount(new BigDecimal(payload.get("amount").toString()))
                .interestRate(new BigDecimal(payload.getOrDefault("interestRate", "0.05").toString()))
                .termMonths((Integer) payload.getOrDefault("termMonths", 12))
                .status("PENDING")
                .build();

        return ResponseEntity.ok(loanRepository.save(loan));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        Loan loan = loanRepository.findById(id).orElseThrow();
        loan.setStatus("APPROVED");
        loanRepository.save(loan);

        // 💰 Generate amortization schedule
        loanRepaymentService.generateAmortizationSchedule(loan);

        return ResponseEntity.ok(Map.of("message", "Loan Approved Successfully", "loanId", id));
    }
}