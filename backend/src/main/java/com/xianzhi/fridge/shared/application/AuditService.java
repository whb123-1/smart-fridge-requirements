package com.xianzhi.fridge.shared.application;

import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.AuditLog;
import com.xianzhi.fridge.shared.infrastructure.AuditLogRepository;
import com.xianzhi.fridge.shared.web.TraceId;
import java.util.UUID;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final ObjectMapper mapper;
    public AuditService(AuditLogRepository repository, ObjectMapper mapper) { this.repository = repository; this.mapper = mapper; }
    public void record(UUID userId, String eventType) {
        record(userId, null, eventType, Map.of());
    }
    public void record(UUID actorId, UUID targetUserId, String eventType, Map<String, ?> metadata) {
        String json = null;
        try { if (metadata != null && !metadata.isEmpty()) json = mapper.writeValueAsString(metadata); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize audit metadata", exception); }
        String traceId = TraceId.get();
        repository.save(new AuditLog(UuidV7.next(), actorId, targetUserId, eventType,
                traceId == null || traceId.isBlank() ? "system" : traceId, json));
    }
}
