package com.example.aml.repository;

import com.example.aml.domain.AppUser;
import com.example.aml.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByVerificationTokenHash(String verificationTokenHash);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRoleAndEnabledTrue(Role role);

    List<AppUser> findAllByOrderByEnabledAscCreatedAtDesc();

    /** Notification recipients: enabled accounts in these roles that have an email address. */
    List<AppUser> findByRoleInAndEnabledTrueAndEmailIsNotNull(Collection<Role> roles);
}
