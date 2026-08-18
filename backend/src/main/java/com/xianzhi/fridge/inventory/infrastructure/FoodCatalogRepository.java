package com.xianzhi.fridge.inventory.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodCatalogRepository extends JpaRepository<FoodCatalog, UUID> {
    List<FoodCatalog> findAllByOrderByCanonicalNameAsc();
    Optional<FoodCatalog> findByCanonicalNameIgnoreCase(String canonicalName);
}
