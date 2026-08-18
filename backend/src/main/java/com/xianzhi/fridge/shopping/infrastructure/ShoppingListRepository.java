package com.xianzhi.fridge.shopping.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
    List<ShoppingList> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<ShoppingList> findFirstByUserIdAndFridgeIdOrderByCreatedAtAsc(UUID userId, UUID fridgeId);
    Optional<ShoppingList> findByIdAndUserId(UUID id, UUID userId);
}
