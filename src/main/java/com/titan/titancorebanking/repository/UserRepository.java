package com.titan.titancorebanking.repository;

import com.titan.titancorebanking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;import com.titan.titancorebanking.entity.User;

// យើងប្រាប់ថា Repository នេះគ្រប់គ្រង "User" ហើយ ID របស់វាប្រភេទ "Long"
public interface UserRepository extends JpaRepository<User, Long> {
    // នៅទីនេះទទេសិន ព្រោះ JpaRepository មានមុខងារ Save/Find ស្រាប់ហើយ
    Optional<User> findByUsername(String username);

    // ✅ បន្ថែម: ពិនិត្យមើលថា email មានគេប្រើហើយឬនៅ?
    boolean existsByEmail(String email);
}