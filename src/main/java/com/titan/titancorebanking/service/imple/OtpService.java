package com.titan.titancorebanking.service.imple;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final Map<String, String> fallback = new ConcurrentHashMap<>();

    private static final String OTP_PREFIX = "OTP:";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    public String generateOtp(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required for OTP generation");
        }

        String otp;
        if (username.startsWith("titan_test_") || username.startsWith("intellij_test")) {
            otp = "123456";
            log.info("🧪 TEST ACCOUNT DETECTED: [{}] - Using fixed OTP for automation", username);
        } else {
            otp = String.format("%06d", (int) (Math.random() * 1000000));
            log.info("🔐 OTP Generated for [{}]", username);
        }

        try {
            redisTemplate.opsForValue().set(OTP_PREFIX + username, otp, OTP_TTL);
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable, storing OTP in-memory");
            fallback.put(username, otp);
        }
        return otp;
    }

    public void validateOtp(String username, String otp) {
        String storedOtp = null;
        try {
            storedOtp = redisTemplate.opsForValue().get(OTP_PREFIX + username);
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable, checking in-memory OTP");
        }

        if (storedOtp == null) {
            storedOtp = fallback.get(username);
        }

        if (storedOtp == null) {
            throw new IllegalArgumentException("❌ OTP expired or not found!");
        }
        if (!storedOtp.equals(otp)) {
            throw new IllegalArgumentException("❌ Invalid OTP!");
        }

        try { redisTemplate.delete(OTP_PREFIX + username); } catch (Exception ignored) {}
        fallback.remove(username);
        log.info("✅ OTP Validated for [{}]", username);
    }
}
