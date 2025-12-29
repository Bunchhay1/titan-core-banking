package com.titan.titancorebanking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService; // យើងប្រើ Service, អត់ប្រើ Repository ផ្ទាល់ទៀតទេ

    // 1. បង្កើត User ថ្មី
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    // 2. មើល User ទាំងអស់ (FIXED ERROR)
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers(); // ✅ ហៅតាមរយៈ Service
    }

    // 3. មើល User តែម្នាក់ (មាន Redis Cache)
    @GetMapping("/{id}")
    public User getOneUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}