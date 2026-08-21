package com.xianzhi.fridge.inventory.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodCatalogAliasRepository extends JpaRepository<FoodCatalogAlias, UUID> {
    Optional<FoodCatalogAlias> findFirstByNormalizedAliasAndApprovedTrue(String normalizedAlias);
}
