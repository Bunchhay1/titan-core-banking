package com.titan.titancorebanking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.titancorebanking.dto.request.AccountRequest;
import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.AccountType;
import com.titan.titancorebanking.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
// ❌ លុប: import org.springframework.boot.test.mock.mockito.MockBean;
// ✅ ដាក់ជំនួសវិញ (Spring Boot 3.4+):
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // ✅ ឥឡូវវាស្គាល់ហើយ
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ✅ ប្រើ @MockitoBean ជំនួស @MockBean
    @MockitoBean
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    // ==========================================
    // 🟢 TEST 1: CREATE ACCOUNT (POST)
    // ==========================================
    @Test
    @WithMockUser(username = "vip_user") // ✅ ឥឡូវវាស្គាល់ហើយ
    void createAccount_ShouldReturn200_WhenValid() throws Exception {
        AccountRequest request = new AccountRequest();
        request.setAccountType("SAVINGS");
        request.setInitialDeposit(new BigDecimal("1000"));
        request.setPin("123456");

        Account mockAccount = Account.builder()
                .accountNumber("999888777")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("1000"))
                .build();

        when(accountService.createAccount(any(AccountRequest.class), eq("vip_user")))
                .thenReturn(mockAccount);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("999888777"))
                .andExpect(jsonPath("$.balance").value(1000));
    }

    // ==========================================
    // 🟢 TEST 2: GET MY ACCOUNTS (GET)
    // ==========================================
    @Test
    @WithMockUser(username = "vip_user")
    void getMyAccounts_ShouldReturnList() throws Exception {
        Account acc1 = Account.builder().accountNumber("111").build();
        Account acc2 = Account.builder().accountNumber("222").build();

        when(accountService.getMyAccounts("vip_user")).thenReturn(List.of(acc1, acc2));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].accountNumber").value("111"));
    }

    // ==========================================
    // 🔴 TEST 3: UNAUTHORIZED ACCESS (403)
    // ==========================================
    @Test
    void createAccount_ShouldFail_WhenNotLoggedIn() throws Exception {
        AccountRequest request = new AccountRequest();
        request.setAccountType("SAVINGS");

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}