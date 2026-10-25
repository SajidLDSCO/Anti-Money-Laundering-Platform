package com.example.aml.api.dto;

import com.example.aml.domain.Role;

import java.time.Instant;

public record UserDto(Long id, String username, String displayName, String email, boolean emailVerified, Role role,
                      boolean enabled, Instant createdAt, Instant lastLoginAt) {
}
