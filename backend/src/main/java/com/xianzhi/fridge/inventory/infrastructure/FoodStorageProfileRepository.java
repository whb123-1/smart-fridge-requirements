package com.xianzhi.fridge.inventory.infrastructure;

import com.xianzhi.fridge.inventory.domain.FoodCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodStorageProfileRepository extends JpaRepository<FoodStorageProfile, UUID> {
    List<FoodStorageProfile> findByCategoryOrderByProfileVersionDesc(FoodCategory category);
}
