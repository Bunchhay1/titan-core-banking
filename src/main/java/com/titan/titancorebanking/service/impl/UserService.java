package com.titan.titancorebanking.service.impl; // 1. កែ Package ឱ្យត្រូវនឹងទីតាំង Folder


import com.titan.titancorebanking.repository.UserRepository; // ត្រូវ Import ព្រោះវាគ្រាន់តែជា Class
import com.titan.titancorebanking.dto.request.UserRegisterRequest;
import com.titan.titancorebanking.dto.response.UserResponse;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    public UserResponse createUser(UserRegisterRequest request) {
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