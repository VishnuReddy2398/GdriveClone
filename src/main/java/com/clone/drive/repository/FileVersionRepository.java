package com.clone.drive.repository;

import com.clone.drive.domain.FileRecord;
import com.clone.drive.domain.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    List<FileVersion> findByFileRecordOrderByVersionNumberDesc(FileRecord fileRecord);
    int countByFileRecord(FileRecord fileRecord);
    Optional<FileVersion> findByStorageKey(String storageKey);
}
