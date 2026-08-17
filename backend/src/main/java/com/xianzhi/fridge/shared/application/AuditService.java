package com.xianzhi.fridge.shared.application;

import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.AuditLog;
import com.xianzhi.fridge.shared.infrastructure.AuditLogRepository;
import com.xianzhi.fridge.shared.web.TraceId;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    public AuditService(AuditLogRepository repository) { this.repository = repository; }
    public void record(UUID userId, String eventType) {
        repository.save(new AuditLog(UuidV7.next(), userId, eventType, TraceId.get(), null));
    }
}
