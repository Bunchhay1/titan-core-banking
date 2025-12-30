package com.titan.titancorebanking.dto.response; // ត្រូវតែជា package នេះ

import lombok.Data;
import java.io.Serializable;

@Data
public class UserResponse implements Serializable {
    private Long id;
    private String username;
    private String email;
    private String fullName;
}