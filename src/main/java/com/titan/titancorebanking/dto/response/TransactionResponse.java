package com.titan.titancorebanking.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private Long id;
    private String type;
    private BigDecimal amount;
    private String note;
    private LocalDateTime timestamp;
    private String status;

    // យើងបង្ហាញតែលេខកុងបានហើយ (មិនបាច់បង្ហាញ Balance ទេ)
    private String fromAccountNumber;
    private String toAccountNumber;

    // ឬបើចង់បង្ហាញឈ្មោះម្ចាស់កុងដែរ (Optional)
    private String fromOwnerName;
    private String toOwnerName;
}