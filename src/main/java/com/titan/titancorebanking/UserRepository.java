package com.titan.titancorebanking;

import org.springframework.data.jpa.repository.JpaRepository;

// យើងប្រាប់ថា Repository នេះគ្រប់គ្រង "User" ហើយ ID របស់វាប្រភេទ "Long"
public interface UserRepository extends JpaRepository<User, Long> {
    // នៅទីនេះទទេសិន ព្រោះ JpaRepository មានមុខងារ Save/Find ស្រាប់ហើយ
}