package com.xianzhi.fridge.inventory.application;

import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalog;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import java.time.Instant;

public interface ShelfLifeEstimator {
    Estimate estimate(InventoryItem item, InventoryBatch batch, FoodCatalog catalog,
                      FoodStorageProfile profile, String zoneKind, Instant now);

    record Estimate(Instant baseExpiryAt, AssessmentSource source, String confidence, String explanation) { }
}
