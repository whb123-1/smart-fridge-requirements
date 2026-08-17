package com.xianzhi.fridge.identity.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSession> findByTokenHash(String tokenHash);
    List<RefreshSession> findByFamilyIdAndRevokedAtIsNull(UUID familyId);
    List<RefreshSession> findByUserIdAndRevokedAtIsNull(UUID userId);
}
