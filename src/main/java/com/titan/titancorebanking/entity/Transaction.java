package com.titan.titancorebanking.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type; // ប្រភេទ: "DEPOSIT", "TRANSFER", "WITHDRAW"

    private BigDecimal amount; // ចំនួនលុយ

    private Long fromAccountId; // លេខ ID គណនីអ្នកផ្ទេរ (អាច Null បើជាការដាក់លុយ)

    private Long toAccountId;   // លេខ ID គណនីអ្នកទទួល

    private LocalDateTime timestamp; // ម៉ោងធ្វើប្រតិបត្តិការ
}