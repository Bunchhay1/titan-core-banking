package com.titan.titancorebanking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    private String firstname;
    private String lastname;
    private String username;
    private String email;

    // ✅ កែប្រែ៖ ប្រើតែ 'password' មួយគត់ និងដាក់ Validation នៅទីនេះ
    @NotBlank(message = "Password cannot be null")
    private String password;

    private String pin;

    // ❌ លុប field 'rawPassword' ចោល (វាមិនចាំបាច់ទេ)

    private String fullName;
}