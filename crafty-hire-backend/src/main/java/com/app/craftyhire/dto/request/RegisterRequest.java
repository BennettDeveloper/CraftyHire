package com.app.craftyhire.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for POST /api/auth/register.
 *
 * Scalability note: Add optional profile fields here as the user model grows
 * (e.g., firstName, lastName, company) without needing a separate endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @Email(message = "Must be a valid email address")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /** Must match 'password' — validated in AuthService, not the DTO layer */
    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}