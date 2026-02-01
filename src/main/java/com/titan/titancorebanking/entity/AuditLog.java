package com.titan.titancorebanking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;    // Who did it?
    private String action;      // What did they do? (e.g., TRANSFER_MONEY)
    private String ipAddress;   // Where from?
    private String status;      // SUCCESS or FAILURE

    private LocalDateTime timestamp;
}