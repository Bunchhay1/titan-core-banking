package com.titan.titancorebanking.repository;

import com.titan.titancorebanking.entity.Account;
import com.titan.titancorebanking.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // ✅ Normal Find (មិនមាន Lock) - សម្រាប់មើល Balance ឬ History
    @Query("SELECT a FROM Account a JOIN FETCH a.user u WHERE u.username = :username")
    List<Account> findByUserUsername(@Param("username") String username);

    // 👇 យើងទុក method ធម្មតានេះមួយ (Optional) បើ Commander ចង់ប្រើនៅកន្លែងផ្សេង
    Optional<Account> findByAccountNumber(String accountNumber);

    // ========================================================
    // 🔐 LOCKING METHOD (សម្រាប់ TransferService ប្រើ)
    // ========================================================
    // ⚠️ សំខាន់: ត្រូវដាក់ឈ្មោះឱ្យដូច Service គឺ "...ForUpdate"
    // ក្នុង AccountRepository.java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a JOIN FETCH a.user WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
    long countByUser(User user);
}