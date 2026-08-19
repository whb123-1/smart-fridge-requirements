package com.xianzhi.fridge.shared.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId, Pageable pageable);
}
