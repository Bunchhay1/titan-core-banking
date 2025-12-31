// ✅ FIX 1: បន្ថែម .impl ដើម្បីឱ្យត្រូវនឹង Folder នៅលើ Server
package com.titan.titancorebanking.service;

import com.titan.titancorebanking.repository.UserRepository;
import com.titan.titancorebanking.dto.request.RegisterRequest; // ✅ ប្រើ DTO នេះជាគោល
import com.titan.titancorebanking.dto.response.UserResponse;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.mapper.UserMapper;
import com.titan.titancorebanking.service.UserService; // (Optional: បើមាន Interface)

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService { // ប្រសិនបើមាន implement interface, ដាក់ implements UserService

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    // ✅ FIX 2: Method នេះប្រើ RegisterRequest
    public UserResponse createUser(RegisterRequest request) {
        // Mapper ត្រូវតែមាន method: toEntity(RegisterRequest request)
        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Cacheable(value = "users_v2", key = "#id")
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userMapper.toResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> userMapper.toResponse(user))
                .collect(Collectors.toList());
    }
}