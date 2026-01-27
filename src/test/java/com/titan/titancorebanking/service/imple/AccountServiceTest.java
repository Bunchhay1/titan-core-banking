package com.titan.titancorebanking.service.imple;


import com.titan.titancorebanking.dto.request.AccountRequest;
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.AccountType;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.repository.AccountRepository;
import com.titan.titancorebanking.repository.TransactionRepository;
import com.titan.titancorebanking.repository.UserRepository;
import com.titan.titancorebanking.service.AccountService;
import com.titan.titancorebanking.service.OtpService;
import com.titan.titancorebanking.service.TitanAiService;
import com.titan.titancorebanking.utils.AccountNumberUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    // 🎭 Mock Dependencies
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpService otpService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TitanAiService titanAiService;

    // 💉 Inject Mocks into Real Service
    @InjectMocks
    private AccountService accountService;

    // ==========================================
    // 🟢 SCENARIO 1: CREATE ACCOUNT (SUCCESS)
    // ==========================================
    @Test
    void createAccount_ShouldSuccess_WhenValid() {
        // GIVEN
        String username = "vip_user";
        User user = User.builder().username(username).id(1L).build();

        AccountRequest request = new AccountRequest();
        request.setAccountType("SAVINGS");
        request.setInitialDeposit(new BigDecimal("1000.00"));
        request.setPin("123456");

        // Mock User Found
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Mock Count (Limit not reached)
        when(accountRepository.countByUser(user)).thenReturn(0L);

        // Mock Save: Return account with ID
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(100L);
            return acc;
        });

        // 🛠️ Mock Static Method (AccountNumberUtils)
        try (MockedStatic<AccountNumberUtils> utilities = mockStatic(AccountNumberUtils.class)) {
            utilities.when(AccountNumberUtils::generateAccountNumber).thenReturn("00120269999");

            // WHEN
            Account createdAccount = accountService.createAccount(request, username);

            // THEN
            assertNotNull(createdAccount);
            assertEquals("00120269999", createdAccount.getAccountNumber());
            assertEquals(new BigDecimal("1000.00"), createdAccount.getBalance());
            assertEquals(AccountType.SAVINGS, createdAccount.getAccountType());

            // Verify
            verify(accountRepository, times(1)).save(any(Account.class));
        }
    }

    // ==========================================
    // 🔴 SCENARIO 2: CREATE ACCOUNT (LIMIT REACHED)
    // ==========================================
    @Test
    void createAccount_ShouldFail_WhenLimitReached() {
        // GIVEN
        String username = "normal_user";
        User user = User.builder().username(username).build();
        AccountRequest request = new AccountRequest();
        request.setAccountType("SAVINGS");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Mock Count = 1 (Limit is 1)
        when(accountRepository.countByUser(user)).thenReturn(1L);

        // WHEN & THEN
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            accountService.createAccount(request, username);
        });

        assertTrue(exception.getMessage().contains("Limit Reached"));
        verify(accountRepository, never()).save(any());
    }

    // ==========================================
    // 🔴 SCENARIO 3: USER NOT FOUND
    // ==========================================
    @Test
    void createAccount_ShouldFail_WhenUserNotFound() {
        String username = "ghost_user";
        AccountRequest request = new AccountRequest();

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            accountService.createAccount(request, username);
        });
    }

    // ==========================================
    // 🟢 SCENARIO 4: GET MY ACCOUNTS
    // ==========================================
    @Test
    void getMyAccounts_ShouldReturnList() {
        String username = "rich_user";
        Account acc1 = Account.builder().accountNumber("111").build();
        Account acc2 = Account.builder().accountNumber("222").build();

        // Mock Repo return 2 accounts
        when(accountRepository.findByUserUsername(username)).thenReturn(List.of(acc1, acc2));

        // WHEN
        List<Account> accounts = accountService.getMyAccounts(username);

        // THEN
        assertEquals(2, accounts.size());
        assertEquals("111", accounts.get(0).getAccountNumber());
    }
}
// ✅ End of class (No extra brackets below this line)