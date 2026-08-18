package com.xianzhi.fridge.speech.infrastructure;

import com.xianzhi.fridge.speech.domain.VoiceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceIngestionRepository extends JpaRepository<VoiceIngestion, UUID> {
    Optional<VoiceIngestion> findByIdAndUserId(UUID id, UUID userId);
    List<VoiceIngestion> findTop100ByStatusInAndExpiresAtBefore(List<VoiceStatus> statuses, Instant expiresAt);
}
