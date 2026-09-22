package com.innerstyle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public-facing user profile. {@code avatarUrl} is an absolute URL (built by the mapper).
 */
@Schema(description = "User profile")
public record UserProfileResponse(
    UUID id,
    String email,
    String fullName,
    String avatarUrl,
    String status,
    boolean emailVerified,
    List<String> roles,
    Instant createdAt
) {
}
