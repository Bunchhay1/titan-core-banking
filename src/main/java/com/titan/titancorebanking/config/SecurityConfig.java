package com.titan.titancorebanking.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // ✅ អនុញ្ញាតឱ្យប្រើ @PreAuthorize លើ Controller (Fine-grained control)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 🔥 CSRF (Cross-Site Request Forgery)
                // យើងបិទ CSRF ចោលព្រោះយើងប្រើ JWT (Stateless)។ CSRF ការពារតែ Session-based Browser attacks ប៉ុណ្ណោះ។
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 🌐 CORS (Cross-Origin Resource Sharing)
                // អនុញ្ញាតឱ្យ Frontend (Web/Mobile) ហៅ API យើងបាន។
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. 🚦 URL Authorization Rules
                .authorizeHttpRequests(auth -> auth
                        // ផ្លូវសាធារណៈ (Public Endpoints) - មិនត្រូវការ Token
                        .requestMatchers(
                                "/api/auth/**",           // Login & Register
                                "/v3/api-docs/**",        // Swagger OpenAPI
                                "/swagger-ui/**",         // Swagger UI
                                "/actuator/**"            // Monitoring (គួរតែបិទនៅ Production)
                        ).permitAll()
                        .requestMatchers("/api/transactions/**").authenticated()
                        // ផ្លូវសម្រាប់តែ Admin
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ផ្លូវផ្សេងទៀតតម្រូវឱ្យមាន Token (Authenticated)
                        .anyRequest().authenticated()
                )

                // 4. 🧠 Session Management
                // កំណត់ជា STATELESS: Server មិនរក្សាទុក Session របស់ User ទេ។
                // រាល់ Request ត្រូវតែភ្ជាប់មកជាមួយ Token ។
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 5. 🔑 Authentication Provider & Filter
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // ដាក់ Filter យើងមុនគេ

        return http.build();
    }

    // ✅ CORS Configuration: កំណត់ថាអ្នកណាខ្លះអាចហៅ API យើងបាន
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ដាក់ Domain Frontend របស់អ្នកនៅទីនេះ (ឧ. "http://localhost:3000")
        // ដាក់ "*" សម្រាប់ការ Test (តែមិនល្អសម្រាប់ Production)
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}