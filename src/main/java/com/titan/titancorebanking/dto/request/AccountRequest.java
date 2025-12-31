package com.titan.titancorebanking.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AccountRequest {

    private String accountType; // "SAVINGS"

    // ✅ យើងប្រើឈ្មោះនេះ ព្រោះក្នុង JSON យើងផ្ញើ "initialDeposit": 5000
    private BigDecimal initialDeposit;

    // ❌ លុប field "balance" ចោល! ដើម្បីកុំឱ្យ Service ច្រឡំយកទៅប្រើ។

    // ⚠️ សំខាន់ណាស់! ត្រូវតែមាន PIN សម្រាប់បង្កើតគណនី
    private String pin;
}