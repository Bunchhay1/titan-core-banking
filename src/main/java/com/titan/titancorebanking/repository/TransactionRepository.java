package com.titan.titancorebanking.repository;

import com.titan.titancorebanking.entity.Transaction;
import org.springframework.data.domain.Page;         // ✅ ថែម Import
import org.springframework.data.domain.Pageable;     // ✅ ថែម Import
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 1. មុខងារចាស់ (List All By User) - រក្សាទុកដដែល
    @Query("SELECT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount f " +
            "LEFT JOIN FETCH f.user fu " +
            "LEFT JOIN FETCH t.toAccount to " +
            "LEFT JOIN FETCH to.user tu " +
            "WHERE fu.username = :username OR tu.username = :username " +
            "ORDER BY t.timestamp DESC")
    List<Transaction> findAllByUser(@Param("username") String username);

    // 2. មុខងារថ្មី (Pagination + N+1 Fix) ✅🛡️
    @Query(value = "SELECT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount f " +   // ✅ Pre-load FromAccount
            "LEFT JOIN FETCH f.user fu " +         // ✅ Pre-load Sender Info
            "LEFT JOIN FETCH t.toAccount to " +    // ✅ Pre-load ToAccount
            "LEFT JOIN FETCH to.user tu " +        // ✅ Pre-load Receiver Info
            "WHERE f.accountNumber = :accountNumber OR to.accountNumber = :accountNumber " +
            "ORDER BY t.timestamp DESC",

            // ⚠️ Count Query: ត្រូវការដាច់ដោយឡែកព្រោះ Hibernate រាប់ចំនួនជាមួយ Fetch មិនបាន
            countQuery = "SELECT count(t) FROM Transaction t " +
                    "LEFT JOIN t.fromAccount f " +
                    "LEFT JOIN t.toAccount to " +
                    "WHERE f.accountNumber = :accountNumber OR to.accountNumber = :accountNumber")
    Page<Transaction> findAllByAccountNumber(@Param("accountNumber") String accountNumber, Pageable pageable);
}