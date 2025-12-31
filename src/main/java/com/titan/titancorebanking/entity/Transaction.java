package com.titan.titancorebanking.entity;

import com.titan.titancorebanking.enums.TransactionStatus; // 👈 Import Enum
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;import com.titan.titancorebanking.enums.TransactionStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) // ✅ Save ជាអក្សរ (SUCCESS, FAILED...)
    private TransactionType type;

    // 👇 1. បន្ថែម Field Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    private LocalDateTime timestamp;

    // 👇 2. បន្ថែម Note ដើម្បីដឹងមូលហេតុ (ឧ. "Incorrect PIN")
    private String note;
}