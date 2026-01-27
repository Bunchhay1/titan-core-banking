package com.titan.titancorebanking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.titancorebanking.dto.request.LoginRequest;
import com.titan.titancorebanking.dto.request.RegisterRequest;
import com.titan.titancorebanking.dto.response.AuthenticationResponse;
import com.titan.titancorebanking.service.imple.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 🎭 Mock Service
    @MockitoBean
    private AuthenticationService authenticationService;

    // ==========================================
    // 📝 TEST 1: REGISTER (SUCCESS)
    // ==========================================
    @Test
    void register_ShouldReturnToken_WhenSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstname("Chhay");
        request.setLastname("Titan");
        request.setUsername("new_user");
        request.setPassword("password");
        request.setPin("123456");

        AuthenticationResponse mockResponse = AuthenticationResponse.builder()
                .token("mock-jwt-token")
                .build();

        when(authenticationService.register(any(RegisterRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"));
    }

    // ==========================================
    // 🔐 TEST 2: LOGIN (SUCCESS)
    // ==========================================
    @Test
    void authenticate_ShouldReturnToken_WhenSuccess() throws Exception {
        LoginRequest request = new LoginRequest("new_user", "password", null);

        AuthenticationResponse mockResponse = AuthenticationResponse.builder()
                .token("login-token-success")
                .build();

        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login-token-success"));
    }

    // ==========================================
    // ⛔ TEST 3: LOGIN FAIL (WRONG PASSWORD)
    // ==========================================
    @Test
    void authenticate_ShouldReturn400_WhenBadCredentials() throws Exception { // ដូរឈ្មោះ Test បន្តិចក៏បាន
        LoginRequest request = new LoginRequest("new_user", "wrong_pass", null);

        // Mock ឱ្យ Service បោះ Error
        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // ❌ ពីមុន: .andExpect(status().isForbidden());
                // ✅ កែទៅជា:
                .andExpect(status().isBadRequest()) // រំពឹងថា 400 វិញ
                .andExpect(jsonPath("$.message").value("Invalid username or password")); // ថែមទាំងឆែក Message ទៀតកាន់តែល្អ!
    }
}