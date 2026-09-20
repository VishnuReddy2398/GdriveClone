package com.clone.drive.repository;

import com.clone.drive.domain.Comment;
import com.clone.drive.domain.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {
    
    // Fetch all top-level comments for a file
    List<Comment> findByFileRecordAndParentCommentIsNullOrderByCreatedAtDesc(FileRecord fileRecord);
    
    // Fetch replies to a specific comment
    List<Comment> findByParentCommentOrderByCreatedAtAsc(Comment parentComment);
    
    // Fetch all comments for a file (flattened)
    List<Comment> findByFileRecordOrderByCreatedAtDesc(FileRecord fileRecord);
}
