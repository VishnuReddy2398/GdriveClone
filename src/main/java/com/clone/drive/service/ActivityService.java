package com.clone.drive.service;

import com.clone.drive.domain.ActivityLog;
import com.clone.drive.domain.User;
import com.clone.drive.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for logging user activity (upload, delete, share, etc.)
 */
@Service
public class ActivityService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public void log(User user, String action, String description, UUID targetFileId, UUID targetFolderId) {
        ActivityLog entry = new ActivityLog();
        entry.setUser(user);
        entry.setAction(action);
        entry.setDescription(description);
        entry.setTargetFileId(targetFileId);
        entry.setTargetFolderId(targetFolderId);
        activityLogRepository.save(entry);
    }

    public List<ActivityLog> getRecentActivity(User user) {
        return activityLogRepository.findTop50ByUserOrderByCreatedAtDesc(user);
    }
}
