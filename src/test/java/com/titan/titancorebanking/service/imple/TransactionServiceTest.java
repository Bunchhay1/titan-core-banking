package com.titan.titancorebanking.service; // ✅ Ensure correct package

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.riskengine.RiskCheckResponse; // ✅ The gRPC version
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import com.titan.titancorebanking.service.RiskEngineGrpcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RiskEngineGrpcService riskEngineGrpcService;
    @Mock private ApplicationEventPublisher eventPublisher; // ✅ Added to match Service

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void transfer_ShouldSuccess_WhenValid() {
        // GIVEN
        String username = "sender_user";
        User senderUser = User.builder().username(username).pin("encodedPin").build();
        Account sender = Account.builder()
                .accountNumber("111")
                .balance(new BigDecimal("1000.00"))
                .user(senderUser)
                .build();
        Account receiver = Account.builder().accountNumber("222").balance(new BigDecimal("500.00")).build();

        TransactionRequest request = new TransactionRequest();
        request.setFromAccountNumber("111");
        request.setToAccountNumber("222");
        request.setAmount(new BigDecimal("200.00"));
        request.setPin("123456");

        // ✅ FIX: Build gRPC Response correctly
        RiskCheckResponse mockRisk = RiskCheckResponse.newBuilder()
                .setAction("ALLOW")
                .build();

        when(accountRepository.findByAccountNumber("111")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber("222")).thenReturn(Optional.of(receiver));
        when(passwordEncoder.matches("123456", "encodedPin")).thenReturn(true);
        when(riskEngineGrpcService.analyzeTransaction(anyString(), anyDouble())).thenReturn(mockRisk);

        // WHEN
        transactionService.transfer(request, username);

        // THEN
        assertEquals(new BigDecimal("800.00"), sender.getBalance());
        assertEquals(new BigDecimal("700.00"), receiver.getBalance());
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void transfer_ShouldBlock_WhenAiRejects() {
        // GIVEN
        String username = "hacker";
        User senderUser = User.builder().username(username).pin("encodedPin").build();
        Account sender = Account.builder()
                .accountNumber("666")
                .balance(new BigDecimal("50000.00"))
                .user(senderUser)
                .build();

        TransactionRequest request = new TransactionRequest();
        request.setFromAccountNumber("666");
        request.setAmount(new BigDecimal("1000.00"));
        request.setPin("123456");

        // ✅ FIX: Build gRPC Response for BLOCK
        RiskCheckResponse mockRisk = RiskCheckResponse.newBuilder()
                .setAction("BLOCK")
                .build();

        when(accountRepository.findByAccountNumber("666")).thenReturn(Optional.of(sender));
        when(passwordEncoder.matches("123456", "encodedPin")).thenReturn(true);
        when(riskEngineGrpcService.analyzeTransaction(anyString(), anyDouble())).thenReturn(mockRisk);

        // WHEN & THEN
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            transactionService.transfer(request, username);
        });

        assertEquals("🚨 Transaction BLOCKED by AI!", exception.getMessage());
        verify(transactionRepository, never()).save(any());
    }
}