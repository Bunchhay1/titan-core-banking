package com.titan.titancorebanking.dto.request; // ត្រូវតែជា package នេះ

import lombok.Data;
import java.io.Serializable;

@Data
public class UserRegisterRequest implements Serializable {
    private String username;
    private String password;
    private String email;
    private String fullName;
}