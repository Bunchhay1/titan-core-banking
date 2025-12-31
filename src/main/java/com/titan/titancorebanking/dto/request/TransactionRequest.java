package com.titan.titancorebanking.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data // ✅ Lombok នឹងបង្កើត getPin() ឱ្យដោយស្វ័យប្រវត្តិ
public class TransactionRequest {
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private String note;

    // 👇 បន្ថែមបន្ទាត់នេះ
    private String pin;
    private String otp;
}