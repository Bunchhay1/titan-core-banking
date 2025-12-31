package com.titan.titancorebanking.mapper;

import com.titan.titancorebanking.dto.request.RegisterRequest;
import com.titan.titancorebanking.entity.User;
import com.titan.titancorebanking.dto.response.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request) {
        if (request == null) {
            return null;
        }
        return User.builder()
                .firstname(request.getFirstname())  // ✅ ដាក់ Firstname
                .lastname(request.getLastname())    // ✅ ដាក់ Lastname
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword()) // ចំណាំ: នៅទីនេះ Password នៅជាអក្សរធម្មតា (Raw)
                .role("ROLE_USER") // កំណត់ Role Default
                .build();
    }

    public UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        return response;
    }

}