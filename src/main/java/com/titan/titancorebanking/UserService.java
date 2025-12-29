package com.titan.titancorebanking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List; // សំខាន់ណាស់! បើអត់មានបន្ទាត់នេះទេ វា Error

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // 1. មុខងារទាញយក User ទាំងអស់ (សម្រាប់ដោះស្រាយ Error នៅ Controller)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 2. មុខងារបង្កើត User ថ្មី
    public User createUser(User user) {
        return userRepository.save(user);
    }

    // 3. មុខងារទាញយក User តែម្នាក់ (ប្រើ Redis Cache)
    // ពេលហៅលើកទី ១: វានឹង Print "🐌 Fetching..."
    // ពេលហៅលើកទី ២: វាស្ងាត់ (ព្រោះយកពី Redis)
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        System.out.println("🐌 Fetching from Database... (Slow)");
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}