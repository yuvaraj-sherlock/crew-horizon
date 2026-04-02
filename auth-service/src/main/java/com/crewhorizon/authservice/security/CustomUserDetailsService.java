package com.crewhorizon.authservice.security;

import com.crewhorizon.authservice.entity.UserEntity;
import com.crewhorizon.authservice.exception.UserNotFoundException;
import com.crewhorizon.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Custom UserDetailsService
 * ============================================================
 * WHAT: Implements Spring Security's UserDetailsService to load
 *       user-specific data during authentication.
 *
 * WHY Custom Implementation (not default):
 *       Spring Security's default UserDetailsService uses an
 *       in-memory user store. We replace it with our JPA-backed
 *       implementation to:
 *       1. Load users from PostgreSQL with full entity data
 *       2. Convert database roles to Spring Security Authorities
 *       3. Handle account lock status and credential expiry
 *       4. Integrate with our custom UserEntity model
 *
 * WHY @Transactional on loadUserByUsername:
 *       The UserEntity has EAGER-loaded roles. Without @Transactional,
 *       the Hibernate session might close before roles are fetched
 *       (despite EAGER, in some transaction boundary scenarios).
 *       @Transactional ensures the session stays open for the
 *       entire method execution.
 *
 * WHY email as username (not employee ID):
 *       Email is universally unique, human-readable, and familiar
 *       to users. Employee IDs can change (transfers, rehires),
 *       but email addresses in airline systems are stable.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by email: {}", email);

        UserEntity user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    // WHY generic message: Revealing "user not found" vs "wrong password"
                    // enables user enumeration attacks. Generic message prevents this.
                    return new UsernameNotFoundException("Invalid credentials");
                });

        /*
         * WHY convert RoleEntity to SimpleGrantedAuthority:
         * Spring Security works with GrantedAuthority objects.
         * Converting our domain roles to Spring's GrantedAuthority
         * enables @PreAuthorize("hasRole('ADMIN')") annotations
         * throughout the application.
         */
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toSet());

        /*
         * WHY use Spring Security's User builder:
         * It validates the UserDetails contract and handles the
         * isEnabled/isLocked flags that Spring Security uses
         * to throw appropriate exceptions (LockedException,
         * DisabledException) during authentication.
         */
        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(!user.getIsAccountNonExpired())
                .accountLocked(!user.getIsAccountNonLocked() || user.isCurrentlyLocked())
                .credentialsExpired(!user.getIsCredentialsNonExpired())
                .disabled(!user.getIsEnabled())
                .build();
    }
}
