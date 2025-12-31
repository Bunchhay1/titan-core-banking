package com.titan.titancorebanking.service;

import com.titan.titancorebanking.exception.InvalidOtpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final StringRedisTemplate redisTemplate;

    // 🔐 ប្រើ SecureRandom ជំនួស Random ធម្មតា ដើម្បីសុវត្ថិភាពខ្ពស់ (Cryptographically strong)
    private final SecureRandom secureRandom = new SecureRandom();

    private static final long OTP_TTL_MINUTES = 5;
    private static final String OTP_PREFIX = "OTP:USER:";

    // 1. GENERATE OTP
    public void generateOtp(String username) {
        // បង្កើតលេខ 6 ខ្ទង់ (100000 -> 999999)
        int code = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(code);

        String key = OTP_PREFIX + username;

        // Save ចូល Redis ជាមួយអាយុកាល 5 នាទី
        redisTemplate.opsForValue().set(key, otpCode, Duration.ofMinutes(OTP_TTL_MINUTES));

        // ⚠️ REAL WORLD: Send Email/SMS here
        // 📝 LAB: Log to console
        log.info("🔐 OTP for user [{}]: {}", username, otpCode);
    }

    // 2. VALIDATE OTP
    public void validateOtp(String username, String inputOtp) {
        String key = OTP_PREFIX + username;
        String cachedOtp = redisTemplate.opsForValue().get(key);

        // Rule 1: បើរកមិនឃើញក្នុង Redis (មានន័យថា Expired ឬមិនទាន់បានស្នើសុំ)
        if (cachedOtp == null) {
            throw new InvalidOtpException("❌ OTP has expired or valid OTP request not found.");
        }

        // Rule 2: បើលេខកូដមិនត្រូវគ្នា
        if (!cachedOtp.equals(inputOtp)) {
            throw new InvalidOtpException("❌ Invalid OTP Code.");
        }

        // Rule 3: បើត្រូវហើយ ត្រូវលុបចោលភ្លាម! (Prevent Replay Attack)
        redisTemplate.delete(key);
        log.info("✅ OTP Verified successfully for user: {}", username);
    }
}