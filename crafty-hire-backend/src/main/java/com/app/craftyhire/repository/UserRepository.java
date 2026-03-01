package com.app.craftyhire.repository;

import com.app.craftyhire.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for User entities.
 *
 * Spring Data automatically implements all standard CRUD methods.
 * Custom queries are defined here as method signatures — Spring
 * derives the SQL from the method name at runtime.
 *
 * Scalability note: Add query methods here as user management features grow
 * (e.g., findByRole, findAllCreatedAfter, countByRole, etc.).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by their email address.
     * Used by UserDetailsService and AuthService for login/token validation.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if an email is already registered.
     * More efficient than findByEmail when we only need existence, not the entity.
     * Used during registration to prevent duplicate accounts.
     */
    boolean existsByEmail(String email);
}