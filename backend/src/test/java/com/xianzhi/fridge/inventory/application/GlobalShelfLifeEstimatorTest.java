package com.xianzhi.fridge.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.domain.FoodCategory;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GlobalShelfLifeEstimatorTest {
    @Test
    void usesGlobalFoodZoneAndStorageContextInsteadOfUserShelfLife() throws Exception {
        Instant storedAt = Instant.parse("2026-08-20T00:00:00Z");
        InventoryItem item = new InventoryItem(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "番茄", FoodCategory.VEGETABLE, null, "g");
        InventoryBatch batch = new InventoryBatch(UUID.randomUUID(), item.getId(), UUID.randomUUID(), storedAt,
                null, null, 999, new BigDecimal("300"), "g", null);
        var constructor = FoodStorageProfile.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        FoodStorageProfile profile = constructor.newInstance();
        ReflectionTestUtils.setField(profile, "unopenedHours", 120);
        ReflectionTestUtils.setField(profile, "openedHours", 72);

        var estimate = new GlobalShelfLifeEstimator().estimate(item, batch, null, profile, "FRESH", storedAt);
        assertThat(estimate.baseExpiryAt()).isEqualTo(storedAt.plusSeconds(120L * 3600));
        assertThat(estimate.source()).isEqualTo(AssessmentSource.CATALOG_PROFILE);
        assertThat(estimate.explanation()).contains("食材档案", "分区目标", "传感器异常").doesNotContain("AI 全局推算");
    }
}
