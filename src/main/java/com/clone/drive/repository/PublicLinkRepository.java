package com.clone.drive.repository;

import com.clone.drive.domain.PublicLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PublicLinkRepository extends JpaRepository<PublicLink, UUID> {
    Optional<PublicLink> findByToken(String token);
}
