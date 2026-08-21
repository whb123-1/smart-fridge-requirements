package com.xianzhi.fridge.inventory.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalog;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class OpenAiShelfLifeEstimator implements ShelfLifeEstimator {
    private static final Set<String> CONFIDENCE = Set.of("LOW", "MEDIUM", "HIGH");
    private final AssistantProperties properties;
    private final ExternalProviderClient client;
    private final ObjectMapper mapper;
    private final GlobalShelfLifeEstimator fallback;

    public OpenAiShelfLifeEstimator(AssistantProperties properties, ExternalProviderClient client,
                                    ObjectMapper mapper, GlobalShelfLifeEstimator fallback) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.fallback = fallback;
    }

    @Override
    public Estimate estimate(InventoryItem item, InventoryBatch batch, FoodCatalog catalog,
                             FoodStorageProfile profile, String zoneKind, Instant now) {
        if (!enabled()) return fallback.estimate(item, batch, catalog, profile, zoneKind, now);
        try {
            var body = mapper.createObjectNode();
            body.put("model", properties.getModelName());
            body.put("temperature", 0.05);
            body.putObject("response_format").put("type", "json_object");
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", """
                    你是食品储存安全期限评估器。结合食材、类别、是否开封、实际已存放时长、储存分区和目录/档案基准，
                    给出从入库或开封锚点开始计算的总安全储存小时数。不得给出超过档案基准 1.5 倍的乐观期限；信息不足时偏保守。
                    只输出 JSON：{"shelfLifeHours":正整数,"confidence":"LOW|MEDIUM|HIGH","reason":"不超过160字中文依据"}。
                    """);
            var input = mapper.createObjectNode();
            input.put("foodName", item.getDisplayName());
            input.put("category", item.getCategory().name());
            input.put("zoneKind", zoneKind == null ? "UNZONED" : zoneKind);
            input.put("storedAt", batch.getStoredAt().toString());
            if (batch.getOpenedAt() != null) input.put("openedAt", batch.getOpenedAt().toString());
            input.put("calculatedAt", now.toString());
            input.put("elapsedHours", Math.max(0, Duration.between(batch.getOpenedAt() == null ? batch.getStoredAt() : batch.getOpenedAt(), now).toHours()));
            if (catalog != null && catalog.getDefaultShelfLifeDays() != null) input.put("catalogDefaultDays", catalog.getDefaultShelfLifeDays());
            Integer profileHours = profile == null ? null : batch.getOpenedAt() == null ? profile.getUnopenedHours() : profile.getOpenedHours();
            if (profileHours != null) input.put("storageProfileHours", profileHours);
            if (profile != null) input.put("riskCoefficient", profile.getRiskCoefficient());
            messages.addObject().put("role", "user").put("content", mapper.writeValueAsString(input));
            JsonNode response = client.postJson("shelf-life-ai", endpoint(properties.getBaseUrl(), "/chat/completions"),
                    properties.getApiKey(), body, properties.getTimeout());
            String content = response.path("choices").path(0).path("message").path("content").asText("");
            JsonNode output = mapper.readTree(content);
            long hours = output.path("shelfLifeHours").asLong(0);
            long upperBound = profileHours == null ? 8_760L : Math.max(1L, Math.round(profileHours * 1.5d));
            if (hours < 1 || hours > upperBound) throw new IllegalStateException("AI shelf-life estimate outside safety bounds");
            String confidence = output.path("confidence").asText("MEDIUM").toUpperCase();
            if (!CONFIDENCE.contains(confidence)) confidence = "MEDIUM";
            String reason = output.path("reason").asText("").trim();
            if (reason.isBlank()) reason = "已综合食材档案、储存状态、分区环境与已存放时长计算。";
            if (reason.length() > 160) reason = reason.substring(0, 160);
            Instant anchor = batch.getOpenedAt() == null ? batch.getStoredAt() : batch.getOpenedAt();
            Instant estimated = anchor.plus(Duration.ofHours(hours));
            if (batch.getPackageExpiresAt() != null && estimated.isAfter(batch.getPackageExpiresAt())) estimated = batch.getPackageExpiresAt();
            return new Estimate(estimated, AssessmentSource.AI_GLOBAL_MODEL, confidence, reason);
        } catch (Exception exception) {
            return fallback.estimate(item, batch, catalog, profile, zoneKind, now);
        }
    }

    private boolean enabled() {
        return properties.isExternalCallsEnabled() && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    private static String endpoint(String base, String path) {
        String value = base.replaceAll("/$", "");
        return value.endsWith(path) ? value : value + path;
    }
}
