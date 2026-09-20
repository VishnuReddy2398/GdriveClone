package com.clone.drive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * User Entity representing a user in the system.
 * Implements Spring Security's UserDetails interface to deeply integrate with
 * the Spring Security ecosystem.
 */
@Entity
@Table(name = "users") // 'user' is often a reserved keyword in Postgres, so we use 'users'
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Email is used as the username for login. Must be unique and not null.
    @Column(unique = true, nullable = false)
    private String email;

    // The hashed password. NEVER store plaintext passwords.
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // --- MFA Fields ---
    @Column
    private String totpSecret;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isMfaEnabled = false;

    // --------------------------------------------------------
    // Spring Security UserDetails Methods
    // --------------------------------------------------------

    /**
     * Returns the authorities granted to the user.
     * For a personal drive, a single USER role is sufficient, 
     * but this can be expanded for admin features.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getUsername() {
        return email; // We use email as the primary login identifier
    }

    @Override
    public String getPassword() {
        return password;
    }

    // Account state flags - essential for robust security
    
    @Override
    public boolean isAccountNonExpired() {
        return true; // Implement logic if accounts should expire
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Implement logic for brute-force lockout prevention
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Implement logic for forced password resets
    }

    @Override
    public boolean isEnabled() {
        return true; // Implement logic for email verification before enabling
    }
}
