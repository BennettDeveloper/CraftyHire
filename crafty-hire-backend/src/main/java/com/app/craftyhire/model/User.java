package com.app.craftyhire.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a registered user of the CraftyHire application.
 *
 * Stored in the database via JPA. For the demo this uses H2 in-memory storage,
 * but the same entity will work with any JPA-compatible database in production.
 *
 * Scalability note: The 'role' field supports future RBAC (Role-Based Access Control).
 * Roles like "ADMIN" or "PREMIUM" can be added without changing the schema.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique email used as the login identifier */
    @Column(unique = true, nullable = false)
    private String email;

    /** Bcrypt-hashed password — never store or log plain text passwords */
    @Column(nullable = false)
    private String passwordHash;

    /**
     * User role for access control.
     * Current values: "USER", "ADMIN"
     * Future: "PREMIUM", "ENTERPRISE", etc.
     */
    @Column(nullable = false)
    @Builder.Default
    private String role = "USER";

    /** Timestamp of when the account was first created */
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}