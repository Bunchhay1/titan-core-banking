package com.titan.titancorebanking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "TRANSFER|DEPOSIT|WITHDRAWAL", message = "Invalid transaction type")
    private String transactionType;

    // ✅ ដាក់ត្រឡប់មកវិញ (To Fix: getFromAccountNumber)
    private String fromAccountNumber;

    // Optional for Deposit/Withdraw, Required for Transfer
    private String toAccountNumber;

    // ✅ ដាក់ត្រឡប់មកវិញ (To Fix: getNote)
    private String note;

    // PIN must be 4 to 6 digits
    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "\\d{4,6}", message = "PIN must be between 4 and 6 digits")
    private String pin;

    // ✅ ដាក់ត្រឡប់មកវិញ (To Fix: getOtp)
    private String otp;
}