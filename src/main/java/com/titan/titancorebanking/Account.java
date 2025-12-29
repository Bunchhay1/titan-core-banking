package com.titan.titancorebanking;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
@Data
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber; // លេខគណនី (ឧ. 001 123 456)

    private BigDecimal balance; // ចំនួនលុយ (ប្រើ BigDecimal សម្រាប់លុយ ដាច់ខាតកុំប្រើ double/float)

    // ភ្ជាប់ទៅ User (Many Accounts belong to One User)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}