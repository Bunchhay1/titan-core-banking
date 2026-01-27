package com.titan.titancorebanking.service.imple;

import com.titan.titancorebanking.dto.request.LoginRequest;
import com.titan.titancorebanking.dto.request.RegisterRequest;
import com.titan.titancorebanking.dto.response.AuthenticationResponse;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthenticationService authenticationService;

    // ==========================================
    // 🟢 SCENARIO 1: REGISTER SUCCESS
    // ==========================================
    @Test
    void register_ShouldReturnToken_WhenValid() {
        // GIVEN
        RegisterRequest request = new RegisterRequest();
        request.setFirstname("Chhay");
        request.setLastname("Titan");
        request.setUsername("chhay_dev");
        request.setPassword("securePass");
        request.setPin("123456");

        // Mock Encoding
        when(passwordEncoder.encode("securePass")).thenReturn("encodedPass");
        when(passwordEncoder.encode("123456")).thenReturn("encodedPin");

        // Mock JWT Generation
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token");

        // WHEN
        AuthenticationResponse response = authenticationService.register(request);

        // THEN
        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());

        // Verify Save was called
        verify(repository, times(1)).save(any(User.class));
    }

    // ==========================================
    // 🟢 SCENARIO 2: LOGIN SUCCESS
    // ==========================================
    @Test
    void authenticate_ShouldReturnToken_WhenCredentialsValid() {
        // GIVEN
        LoginRequest request = new LoginRequest("chhay_dev", "securePass", null);
        User user = User.builder().username("chhay_dev").password("encodedPass").build();

        // Mock AuthenticationManager (Pass through)
        // មិនបាច់ Mock return អីទេ គ្រាន់តែមិន Throw Exception គឺមានន័យថា Login ត្រូវ
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);

        // Mock Database Finding User
        when(repository.findByUsername("chhay_dev")).thenReturn(Optional.of(user));

        // Mock JWT
        when(jwtService.generateToken(user)).thenReturn("login-token");

        // WHEN
        AuthenticationResponse response = authenticationService.authenticate(request);

        // THEN
        assertNotNull(response);
        assertEquals("login-token", response.getToken());
    }

    // ==========================================
    // 🔴 SCENARIO 3: LOGIN FAILED (WRONG PASSWORD)
    // ==========================================
    @Test
    void authenticate_ShouldThrowException_WhenCredentialsInvalid() {
        // GIVEN
        LoginRequest request = new LoginRequest("chhay_dev", "wrongPass", null);

        // Mock Manager to Throw Exception (ដូច Spring Security ធ្វើពេលខុស Password)
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // WHEN & THEN
        assertThrows(BadCredentialsException.class, () -> {
            authenticationService.authenticate(request);
        });

        // ត្រូវប្រាកដថា មិនមានការបង្កើត Token ទេ
        verify(jwtService, never()).generateToken(any());
    }
}