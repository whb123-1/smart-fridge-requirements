package com.xianzhi.fridge.inventory.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodWeightEstimateRepository extends JpaRepository<FoodWeightEstimate, UUID> {
    List<FoodWeightEstimate> findByCatalogIdOrderByLabelAsc(UUID catalogId);
}
