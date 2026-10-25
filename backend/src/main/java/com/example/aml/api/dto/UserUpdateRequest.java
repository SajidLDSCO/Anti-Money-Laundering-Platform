package com.example.aml.api.dto;

import com.example.aml.domain.Role;

/** Admin change to a user. Null fields are left as they are. */
public record UserUpdateRequest(Role role, Boolean enabled) {
}
