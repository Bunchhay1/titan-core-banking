package com.titan.titancorebanking.service.imple;

import com.titan.titancorebanking.dto.request.TransactionRequest;
import com.titan.titancorebanking.dto.response.RiskCheckResponse;
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import com.titan.titancorebanking.service.NotificationService;
// ✅ Import ពី Package imple តាមរចនាសម្ព័ន្ធរបស់លោក
import com.titan.titancorebanking.service.RiskEngineGrpcService;
import com.titan.titancorebanking.service.TransactionService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;
    @Mock private RiskEngineGrpcService riskEngineGrpcService;

    @InjectMocks
    private TransactionService transactionService;

    // ==========================================
    // 🟢 SCENARIO 1: SUCCESSFUL TRANSFER
    // ==========================================
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

        // Mocking
        when(accountRepository.findByAccountNumber("111")).thenReturn(Optional.of(sender));
        when(passwordEncoder.matches("123456", "encodedPin")).thenReturn(true);

        // ✅ Fix 1: ប្រើ analyzeTransaction ជំនួស evaluateRisk
        // ✅ Fix 2: RiskCheckResponse ជា Record, ប្រើ Constructor ផ្ទាល់
        when(riskEngineGrpcService.analyzeTransaction(any(), any()))
                .thenReturn(new RiskCheckResponse("LOW", "ALLOW"));

        when(accountRepository.findByAccountNumber("222")).thenReturn(Optional.of(receiver));

        // WHEN (ហៅ method ឈ្មោះ 'transfer' ជំនួស 'transferFunds')
        transactionService.transfer(request, username);

        // THEN
        assertEquals(new BigDecimal("800.00"), sender.getBalance());
        assertEquals(new BigDecimal("700.00"), receiver.getBalance());

        // ពិនិត្យមើលថាវា Save ចូល Database
        verify(accountRepository, times(1)).save(sender);
        verify(transactionRepository, times(1)).save(any());
        verify(notificationService, times(1)).sendNotification(any(), any());
    }

    // ==========================================
    // 🔴 SCENARIO 2: INSUFFICIENT FUNDS
    // ==========================================
    @Test
    void transfer_ShouldFail_WhenInsufficientFunds() {
        String username = "sender_user";
        User senderUser = User.builder().username(username).pin("encodedPin").build();

        Account sender = Account.builder()
                .accountNumber("111")
                .balance(new BigDecimal("50.00")) // មានលុយតិច
                .user(senderUser)
                .build();

        TransactionRequest request = new TransactionRequest();
        request.setFromAccountNumber("111");
        request.setAmount(new BigDecimal("200.00")); // ចង់វេរច្រើន
        request.setPin("123456");

        when(accountRepository.findByAccountNumber("111")).thenReturn(Optional.of(sender));
        when(passwordEncoder.matches("123456", "encodedPin")).thenReturn(true);

        // Mock AI ALLOW (ទោះ AI ឱ្យ ក៏លុយអត់គ្រប់ដែរ)
        when(riskEngineGrpcService.analyzeTransaction(any(), any()))
                .thenReturn(new RiskCheckResponse("LOW", "ALLOW"));

        // WHEN & THEN
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            transactionService.transfer(request, username);
        });

        assertEquals("❌ Insufficient balance", exception.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    // ==========================================
    // 🛡️ SCENARIO 3: AI RISK BLOCK
    // ==========================================
    @Test
    void transfer_ShouldBlock_WhenAiRejects() {
        String username = "hacker";
        User senderUser = User.builder().username(username).pin("encodedPin").build();

        Account sender = Account.builder()
                .accountNumber("666")
                .balance(new BigDecimal("50000.00")) // ✅ ត្រូវដាក់ Balance ដើម្បីការពារ NPE
                .user(senderUser)
                .build();

        TransactionRequest request = new TransactionRequest();
        request.setFromAccountNumber("666");
        request.setAmount(new BigDecimal("50000.00"));
        request.setPin("123456");

        when(accountRepository.findByAccountNumber("666")).thenReturn(Optional.of(sender));
        when(passwordEncoder.matches("123456", "encodedPin")).thenReturn(true);

        // ✅ Mock AI BLOCK
        // ចំណាំ: ដោយសារ RiskCheckResponse ជា Record សូមប្រើ Constructor ឱ្យត្រូវ
        // ការពារ NPE ដោយប្រើ Constructor របស់ Record
        when(riskEngineGrpcService.analyzeTransaction(any(), any()))
                .thenReturn(new RiskCheckResponse("HIGH", "BLOCK")); // ✅ មិនបាច់ setAction ទេ

        // WHEN & THEN
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            transactionService.transfer(request, username);
        });

        // ឥឡូវវានឹងចាប់បាន Message ត្រឹមត្រូវហើយ!
        assertEquals("🚨 Transaction BLOCKED by AI!", exception.getMessage());

        // ត្រូវប្រាកដថាមិនមានការកាត់លុយ
        verify(transactionRepository, never()).save(any());
    }
}