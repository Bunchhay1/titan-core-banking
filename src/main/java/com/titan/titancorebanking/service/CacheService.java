package com.titan.titancorebanking.service;

import com.titan.titancorebanking.model.User;
import com.titan.titancorebanking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching service for high-read/low-write data.
 * Uses Redis for distributed caching across instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {
    
    private final UserRepository userRepository;
    
    // In-memory cache for exchange rates (updated every 5 minutes)
    private final Map<String, BigDecimal> exchangeRateCache = new ConcurrentHashMap<>();
    
    @Cacheable(value = "user-profiles", key = "#username", unless = "#result == null")
    public User getUserProfile(String username) {
        log.debug("Cache miss - fetching user profile: {}", username);
        return userRepository.findByUsername(username).orElse(null);
    }
    
    @CacheEvict(value = "user-profiles", key = "#username")
    public void evictUserProfile(String username) {
        log.info("Evicted user profile from cache: {}", username);
    }
    
    @Cacheable(value = "exchange-rates", key = "#fromCurrency + '-' + #toCurrency")
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        String key = fromCurrency + "-" + toCurrency;
        BigDecimal rate = exchangeRateCache.get(key);
        
        if (rate == null) {
            // Simulate external API call
            rate = fetchExchangeRateFromApi(fromCurrency, toCurrency);
            exchangeRateCache.put(key, rate);
            log.info("Cached exchange rate: {} = {}", key, rate);
        }
        
        return rate;
    }
    
    private BigDecimal fetchExchangeRateFromApi(String from, String to) {
        // Simulate API latency
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Mock rates
        return switch (from + "-" + to) {
            case "USD-EUR" -> new BigDecimal("0.85");
            case "EUR-USD" -> new BigDecimal("1.18");
            case "USD-KHR" -> new BigDecimal("4100.00");
            case "KHR-USD" -> new BigDecimal("0.000244");
            default -> BigDecimal.ONE;
        };
    }
}
