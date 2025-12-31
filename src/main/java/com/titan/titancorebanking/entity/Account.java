package com.titan.titancorebanking.entity;

import com.titan.titancorebanking.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;import java.io.Serializable;import com.titan.titancorebanking.enums.AccountStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts")
// 👇 2. ត្រូវតែមាន implements Serializable ទើប Redis ស្គាល់
public class Account implements Serializable {

    // 👇 3. លេខសម្គាល់ Version (ការពារ Error ពេលកែ Class ថ្ងៃក្រោយ)
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType; // ✅ ត្រូវតែជា AccountType (Enum)

    // ✅ បន្ថែមបន្ទាត់នេះចូល (The Missing Piece)
    private String currency;

    @Column(nullable = false, precision = 30, scale = 2)
    private BigDecimal balance;

    // ⚠️ ចំណាំ: User ក៏ត្រូវតែ Serializable ដែរ!
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @Enumerated(EnumType.STRING) // ✅ សំខាន់! ដើម្បីឱ្យវា Save ជាអក្សរ "ACTIVE" ចូល DB
    private AccountStatus status;
}