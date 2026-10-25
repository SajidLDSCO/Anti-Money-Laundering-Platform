package com.example.aml.api;

import com.example.aml.api.dto.UserDto;
import com.example.aml.api.dto.UserUpdateRequest;
import com.example.aml.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** User administration. ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserDto> list() {
        return users.list();
    }

    @PatchMapping("/{id}")
    public UserDto update(@PathVariable Long id, @RequestBody UserUpdateRequest request, Authentication auth) {
        return users.update(id, request, auth.getName());
    }

    /** Declines a sign-up that was never approved (deletes it and tells the person). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long id) {
        users.decline(id);
    }
}
