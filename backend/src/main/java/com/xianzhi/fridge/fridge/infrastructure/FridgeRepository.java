package com.xianzhi.fridge.fridge.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeRepository extends JpaRepository<Fridge, UUID> {
    List<Fridge> findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID userId);
}
