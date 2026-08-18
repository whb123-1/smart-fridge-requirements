package com.xianzhi.fridge.shared.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecord;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecordRepository;
import com.xianzhi.fridge.shared.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository records;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository records, ObjectMapper mapper, Clock clock) {
        this.records = records; this.mapper = mapper; this.clock = clock;
    }

    public <T> T replay(UUID userId, String key, String method, String path, Object request, Class<T> responseType) {
        requireKey(key);
        IdempotencyRecord previous = records.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (previous == null) return null;
        String fingerprint = fingerprint(method, path, request);
        if (!previous.getRequestHash().equals(fingerprint)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for a different request");
        }
        try { return mapper.readValue(previous.getResponseBody(), responseType); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not read idempotency response", exception); }
    }

    public void save(UUID userId, String key, String method, String path, Object request, Object response, int status) {
        try {
            records.save(new IdempotencyRecord(UuidV7.next(), userId, key, fingerprint(method, path, request),
                    mapper.writeValueAsString(response), clock.instant().plus(Duration.ofDays(7)), method, path, status));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store idempotency response", exception);
        }
    }

    public String fingerprint(String method, String path, Object request) {
        try { return Hashing.sha256(method + " " + path + "\n" + mapper.writeValueAsString(request)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not hash request", exception); }
    }

    public static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key is required");
        }
    }
}
