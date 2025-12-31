package com.titan.titancorebanking.controller;

import com.titan.titancorebanking.dto.request.AccountRequest;
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // ✅ ទុកតែមួយនេះបានហើយ (The New Logic)
    @PostMapping
    public ResponseEntity<Account> createAccount(
            @RequestBody AccountRequest request,
            Principal principal // ចាប់យក User ពី Token
    ) {
        // principal.getName() នឹងផ្តល់ឱ្យយើងនូវ Username
        return ResponseEntity.ok(
                accountService.createAccount(request, principal.getName())
        );
    }

    @GetMapping
    public ResponseEntity<List<Account>> getMyAccounts(Principal principal) {
        return ResponseEntity.ok(
                accountService.getMyAccounts(principal.getName())
        );
    }
    @GetMapping("/balance/{accountNumber}")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountNumber) {
        // ហៅទៅ Service (ត្រូវប្រាកដថា Service មាន method getBalance ដែរ)
        BigDecimal balance = accountService.getBalance(accountNumber);
        return ResponseEntity.ok(balance);
    }
}