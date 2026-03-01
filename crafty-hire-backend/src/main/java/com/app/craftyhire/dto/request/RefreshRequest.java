package com.app.craftyhire.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for POST /api/auth/refresh.
 * Used to obtain a new access token when the current one has expired,
 * without requiring the user to log in again.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}