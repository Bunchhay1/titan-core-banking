package com.titan.titancorebanking.enums;

public enum TransactionStatus {
    PENDING,    // ⏳ កំពុងរង់ចាំ
    PROCESSING, // ⚙️ កំពុងដំណើរការ
    SUCCESS,    // ✅ ជោគជ័យ
    FAILED,     // ❌ បរាជ័យ
    REVERSED    // ↩️ ត្រឡប់វិញ (Rollback)
}