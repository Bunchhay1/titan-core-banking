package com.titan.titancorebanking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstname;
    private String lastname;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore
    private String pin;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    private String email;

    @JsonIgnore
    private String role;

    // 👇 1. បន្ថែម Field នេះដើម្បី Lock Account បាន (សំខាន់ណាស់!)
    @Builder.Default // ប្រើ Default=true ដើម្បីកុំឱ្យ User ថ្មីជាប់សោរ
    @Column(name = "account_non_locked")
    private boolean accountNonLocked = true;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnore
    private List<Account> accounts;

    public String getFullName() {
        return firstname + " " + lastname;
    }

    // 👇👇👇 Spring Security Methods 👇👇👇

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null || role.isEmpty()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    // 👇 2. Update Method នេះឱ្យ Return តម្លៃពី Field ខាងលើ
    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return accountNonLocked; // ✅ លែង return true រហូតហើយ
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }
}