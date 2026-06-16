package com.titan.titancorebanking.service;

import com.titan.titancorebanking.model.Loan;
import com.titan.titancorebanking.model.ScheduledTransaction;
import com.titan.titancorebanking.repository.ScheduledTransactionRepository;
import com.titan.titancorebanking.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanRepaymentService {

    private final ScheduledTransactionRepository scheduledTransactionRepository;

    @Transactional
    public List<ScheduledTransaction> generateAmortizationSchedule(Loan loan) {
        int n = loan.getTermMonths();
        BigDecimal P = loan.getAmount();
        BigDecimal r = loan.getInterestRate().divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        
        // Fixed monthly payment: M = P * [r(1+r)^n] / [(1+r)^n - 1]
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        BigDecimal onePlusRPowN = onePlusR.pow(n);
        BigDecimal numerator = P.multiply(r).multiply(onePlusRPowN);
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);
        BigDecimal monthlyPayment = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        
        List<ScheduledTransaction> schedule = new ArrayList<>();
        LocalDateTime nextPaymentDate = LocalDateTime.now().plusMonths(1);
        
        for (int i = 0; i < n; i++) {
            ScheduledTransaction payment = ScheduledTransaction.builder()
                .fromAccountId(loan.getAccount().getId())
                .toAccountId(loan.getAccount().getId()) // Bank receives payment (same account as placeholder)
                .amount(monthlyPayment)
                .transactionType(TransactionType.LOAN_REPAYMENT)
                .scheduledDate(nextPaymentDate)
                .status("PENDING")
                .build();
            
            schedule.add(payment);
            nextPaymentDate = nextPaymentDate.plusMonths(1);
        }
        
        scheduledTransactionRepository.saveAll(schedule);
        log.info("📅 Generated {} loan repayment schedules for Loan ID: {}", n, loan.getId());
        
        return schedule;
    }
}
