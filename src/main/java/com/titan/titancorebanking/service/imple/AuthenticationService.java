package com.titan.titancorebanking.service.imple;

import com.titan.titancorebanking.dto.request.LoginRequest;
import com.titan.titancorebanking.dto.request.RegisterRequest;
import com.titan.titancorebanking.dto.response.AuthenticationResponse;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // 1. ចុះឈ្មោះ (Register) -> បាន Token
    public AuthenticationResponse register(RegisterRequest request) {
        var user = User.builder()
                .firstname(request.getFirstname()) // ✅ ប្រើ firstname
                .lastname(request.getLastname())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .pin(passwordEncoder.encode(request.getPin()))
                .role("ROLE_USER")
                .build();

        repository.save(user); // Save ចូល Database

        var jwtToken = jwtService.generateToken(user); // បង្កើត Token
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    // 2. ចូលប្រព័ន្ធ (Login) -> បាន Token
    public AuthenticationResponse authenticate(LoginRequest request) {
        // ពិនិត្យមើលថា Username/Password ត្រូវអត់? (បើខុស វានឹង Error ត្រង់នេះ)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // បើត្រូវហើយ, ទៅយកព័ត៌មាន User មក
        var user = repository.findByUsername(request.getUsername())
                .orElseThrow();

        // បង្កើត Token ថ្មីជូនគាត់
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }
}