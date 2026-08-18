package com.xianzhi.fridge.shopping.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, UUID> {
    List<ShoppingItem> findByListIdOrderByCreatedAtDesc(UUID listId);
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShoppingItem> findById(UUID id);
}
