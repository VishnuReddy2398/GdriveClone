package com.clone.drive.controller;

import com.clone.drive.domain.Comment;
import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.Permission;
import com.clone.drive.domain.User;
import com.clone.drive.repository.CommentRepository;
import com.clone.drive.repository.FileRecordRepository;
import com.clone.drive.repository.UserRepository;
import com.clone.drive.service.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/files/{fileId}/comments")
public class CommentController {

    private final CommentRepository commentRepository;
    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authService;

    public CommentController(CommentRepository commentRepository,
                             FileRecordRepository fileRecordRepository,
                             UserRepository userRepository,
                             AuthorizationService authService) {
        this.commentRepository = commentRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Get all comments for a file. Requires VIEWER access.
     */
    @GetMapping
    public ResponseEntity<?> getComments(@PathVariable UUID fileId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> fileOpt = fileRecordRepository.findById(fileId);

        if (fileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord file = fileOpt.get();
        if (!authService.canAccessFile(user, file, Permission.PermissionLevel.VIEWER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to view this file");
        }

        return ResponseEntity.ok(commentRepository.findByFileRecordAndParentCommentIsNullOrderByCreatedAtDesc(file));
    }

    /**
     * Add a comment to a file. Requires COMMENTER access.
     */
    @PostMapping
    public ResponseEntity<?> addComment(@PathVariable UUID fileId, 
                                        @RequestParam String content,
                                        @RequestParam(required = false) UUID parentId) {
        User user = getAuthenticatedUser();
        Optional<FileRecord> fileOpt = fileRecordRepository.findById(fileId);

        if (fileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        FileRecord file = fileOpt.get();
        if (!authService.canAccessFile(user, file, Permission.PermissionLevel.COMMENTER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to comment on this file");
        }

        Comment comment = new Comment();
        comment.setFileRecord(file);
        comment.setUser(user);
        comment.setContent(content);

        if (parentId != null) {
            Optional<Comment> parentOpt = commentRepository.findById(parentId);
            if (parentOpt.isPresent() && parentOpt.get().getFileRecord().getId().equals(fileId)) {
                comment.setParentComment(parentOpt.get());
            } else {
                return ResponseEntity.badRequest().body("Invalid parent comment");
            }
        }

        commentRepository.save(comment);

        // TODO: Publish Notification event (e.g. "User X commented on your file") via RabbitMQ

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    /**
     * Edit a comment. Must be the original author.
     */
    @PutMapping("/{commentId}")
    public ResponseEntity<?> editComment(@PathVariable UUID fileId, 
                                         @PathVariable UUID commentId, 
                                         @RequestParam String newContent) {
        User user = getAuthenticatedUser();
        Optional<Comment> commentOpt = commentRepository.findById(commentId);

        if (commentOpt.isEmpty() || !commentOpt.get().getFileRecord().getId().equals(fileId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
        }

        Comment comment = commentOpt.get();
        if (!comment.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only edit your own comments");
        }

        comment.setContent(newContent);
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.save(comment);

        return ResponseEntity.ok(comment);
    }

    /**
     * Delete a comment. Must be the author or have OWNER access to the file.
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable UUID fileId, @PathVariable UUID commentId) {
        User user = getAuthenticatedUser();
        Optional<Comment> commentOpt = commentRepository.findById(commentId);

        if (commentOpt.isEmpty() || !commentOpt.get().getFileRecord().getId().equals(fileId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
        }

        Comment comment = commentOpt.get();
        boolean isAuthor = comment.getUser().getId().equals(user.getId());
        boolean isFileOwner = authService.canAccessFile(user, comment.getFileRecord(), Permission.PermissionLevel.OWNER);

        if (!isAuthor && !isFileOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to delete this comment");
        }

        commentRepository.delete(comment);
        return ResponseEntity.ok(Map.of("message", "Comment deleted"));
    }

    /**
     * Resolve a comment thread. Requires EDITOR access.
     */
    @PostMapping("/{commentId}/resolve")
    public ResponseEntity<?> resolveComment(@PathVariable UUID fileId, @PathVariable UUID commentId) {
        User user = getAuthenticatedUser();
        Optional<Comment> commentOpt = commentRepository.findById(commentId);

        if (commentOpt.isEmpty() || !commentOpt.get().getFileRecord().getId().equals(fileId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
        }

        Comment comment = commentOpt.get();
        if (!authService.canAccessFile(user, comment.getFileRecord(), Permission.PermissionLevel.EDITOR)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Requires EDITOR access to resolve comments");
        }

        comment.setResolved(true);
        commentRepository.save(comment);
        return ResponseEntity.ok(comment);
    }
}
