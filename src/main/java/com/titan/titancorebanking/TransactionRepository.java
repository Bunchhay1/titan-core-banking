package com.titan.titancorebanking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // មុខងារទាញយកប្រវត្តិ តាមរយៈ Account ID (អ្នកទទួល ឬ អ្នកផ្ទេរ)
    List<Transaction> findByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId);
}