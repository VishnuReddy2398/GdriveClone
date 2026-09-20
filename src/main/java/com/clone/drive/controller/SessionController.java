package com.clone.drive.controller;

import com.clone.drive.domain.User;
import com.clone.drive.domain.UserSession;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.repository.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;

    public SessionController(UserSessionRepository userSessionRepository, UserRepository userRepository) {
        this.userSessionRepository = userSessionRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    @GetMapping
    public ResponseEntity<?> getActiveSessions() {
        User user = getAuthenticatedUser();
        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndIsRevokedFalse(user.getId());
        
        List<Map<String, Object>> sessionDtos = activeSessions.stream().map(session -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", session.getId().toString());
            map.put("ipAddress", session.getIpAddress());
            map.put("userAgent", session.getUserAgent());
            map.put("createdAt", session.getCreatedAt());
            map.put("lastActiveAt", session.getLastActiveAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(sessionDtos);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revokeSession(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<UserSession> sessionOpt = userSessionRepository.findById(id);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Session not found");
        }

        UserSession session = sessionOpt.get();
        if (!session.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }

        session.setRevoked(true);
        userSessionRepository.save(session);

        return ResponseEntity.ok("Session revoked successfully");
    }
}
