package com.clone.drive.repository;

import com.clone.drive.domain.ActivityLog;
import com.clone.drive.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    List<ActivityLog> findTop50ByUserOrderByCreatedAtDesc(User user);
}
