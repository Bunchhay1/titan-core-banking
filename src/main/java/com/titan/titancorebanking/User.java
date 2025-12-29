package com.titan.titancorebanking;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Entity // ប្រាប់ Spring ថា Class នេះគឺជា Table ក្នុង Database
@Table(name = "users") // ដាក់ឈ្មោះ Table ក្នុង Postgres ថា "users"
@Data // (Lombok) បង្កើត Getters/Setters ឱ្យស្វ័យប្រវត្តិ
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // ID កើនឡើងរហូតដោយខ្លួនឯង (Auto Increment)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private String password;

    private String email;
}