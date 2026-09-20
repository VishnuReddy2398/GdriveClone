package com.clone.drive.repository;

import com.clone.drive.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Used by Spring Security to look up a user during authentication.
     */
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
}
