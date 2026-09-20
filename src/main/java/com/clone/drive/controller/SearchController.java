package com.clone.drive.controller;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.User;
import com.clone.drive.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Search controller using Postgres full-text search (tsvector/tsquery).
 * Supports searching by filename across all of a user's files.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final EntityManager entityManager;
    private final UserRepository userRepository;

    public SearchController(EntityManager entityManager, UserRepository userRepository) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Search files by name using Postgres ILIKE for simplicity.
     * For production, upgrade to tsvector + ts_rank for ranked full-text search.
     */
    @GetMapping
    public ResponseEntity<?> search(@RequestParam String q) {
        User user = getAuthenticatedUser();

        // Use native query with ILIKE for case-insensitive partial matching
        String sql = "SELECT * FROM file_records WHERE owner_id = :ownerId " +
                     "AND deleted_at IS NULL " +
                     "AND LOWER(name) LIKE LOWER(:query) " +
                     "ORDER BY created_at DESC";

        Query query = entityManager.createNativeQuery(sql, FileRecord.class);
        query.setParameter("ownerId", user.getId());
        query.setParameter("query", "%" + q + "%");

        @SuppressWarnings("unchecked")
        List<FileRecord> results = query.getResultList();

        return ResponseEntity.ok(results);
    }
}
