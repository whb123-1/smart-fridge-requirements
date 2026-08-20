package com.xianzhi.fridge.inventory.application;

import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalog;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class GlobalShelfLifeEstimator implements ShelfLifeEstimator {
    @Override
    public Estimate estimate(InventoryItem item, InventoryBatch batch, FoodCatalog catalog,
                             FoodStorageProfile profile, String zoneKind, Instant now) {
        Instant anchor = batch.getOpenedAt() == null ? batch.getStoredAt() : batch.getOpenedAt();
        Integer hours = profile == null ? null : batch.getOpenedAt() == null ? profile.getUnopenedHours() : profile.getOpenedHours();
        if (hours != null) {
            String explanation = "AI 全局推算：结合“" + item.getDisplayName() + "”食材档案、"
                    + (zoneKind == null ? "未分区储存" : zoneKind + " 分区目标")
                    + (batch.getOpenedAt() == null ? "、入库时间与未开封状态" : "、开封时间")
                    + "生成基础期限；传感器异常会继续单向缩短。";
            return new Estimate(anchor.plus(Duration.ofHours(hours)), AssessmentSource.CATALOG_PROFILE, "MEDIUM", explanation);
        }
        if (catalog != null && catalog.getDefaultShelfLifeDays() != null) {
            return new Estimate(batch.getStoredAt().plus(Duration.ofDays(catalog.getDefaultShelfLifeDays())),
                    AssessmentSource.REFERENCE_DEFAULT, "MEDIUM",
                    "AI 全局推算：当前分区没有专属储存档案，已结合食材目录参考期限与入库时间估算；后续环境风险会继续联动。");
        }
        return new Estimate(null, AssessmentSource.REFERENCE_TARGET, "LOW",
                "AI 全局推算尚缺少该自定义食材的可靠档案；系统会保留分区和传感器上下文，补充模型依据后自动重算。");
    }
}
