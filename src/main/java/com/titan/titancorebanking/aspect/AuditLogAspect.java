package com.titan.titancorebanking.aspect;

import com.titan.titancorebanking.annotation.AuditLog;
import com.titan.titancorebanking.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {

        // 1. Capture Start Info
        String username = "Anonymous";
        String ipAddress = "Unknown";
        String status = "SUCCESS";

        try {
            // Get Username from Security Context
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                username = SecurityContextHolder.getContext().getAuthentication().getName();
            }

            // Get IP Address
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            ipAddress = request.getRemoteAddr();

        } catch (Exception e) {
            // Ignore (Keep logging even if context is missing)
        }

        // 2. Execute the Method
        Object result;
        try {
            result = joinPoint.proceed(); // Run the actual method (e.g., transferMoney)
        } catch (Throwable e) {
            status = "FAILURE"; // If method fails, record FAILURE
            throw e; // Re-throw exception so the App handles it
        } finally {
            // 3. Save Log to Database (Finally block ensures it runs even on error)
            saveLog(username, auditLog.action(), ipAddress, status);
        }

        return result;
    }

    private void saveLog(String username, String action, String ip, String status) {
        var logEntry = com.titan.titancorebanking.entity.AuditLog.builder()
                .username(username)
                .action(action)
                .ipAddress(ip)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(logEntry);
        log.info("📼 AUDIT: User [{}] performed [{}] -> Status: [{}]", username, action, status);
    }
}