package com.titan.titancorebanking.repository;

import com.titan.titancorebanking.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    // យើងអាចបង្កើតមុខងារស្វែងរកនៅទីនេះបាន
    // ឧទាហរណ៍: រកមើល Account ទាំងអស់របស់ User ណាម្នាក់
    // List<Account> findByUserId(Long userId);
}