package com.xianzhi.fridge.recipe.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OpenAiRecipeGenerationAdapter implements RecipeGenerationPort {
    private final AssistantProperties properties;
    private final ExternalProviderClient client;
    private final ObjectMapper mapper;

    public OpenAiRecipeGenerationAdapter(AssistantProperties properties, ExternalProviderClient client, ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public GeneratedRecipes rank(String prompt, List<Candidate> candidates, List<RecipeContracts.IngredientInput> inventory,
                                 List<String> tastes, List<String> cuisines, String goal, Integer calorieTarget, int count) {
        List<UUID> fallback = candidates.stream().limit(count).map(Candidate::id).toList();
        if (!properties.isExternalCallsEnabled() || properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || candidates.isEmpty()) return new GeneratedRecipes(fallback, "根据库存、偏好和营养目标排序", "rules-v2", true);
        try {
            var body = mapper.createObjectNode();
            body.put("model", properties.getModelName());
            body.put("temperature", 0.1);
            body.putObject("response_format").put("type", "json_object");
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content",
                    "你是菜谱推荐器。只能从候选 ID 中选择，不能编造 ID 或食材。严格输出 JSON："
                            + "{\"recipeIds\":[\"候选ID\"],\"rationale\":\"不超过120字中文理由\"}。");
            var request = mapper.createObjectNode();
            request.put("prompt", prompt == null ? "" : prompt);
            List<Map<String, Object>> inventoryPayload = new ArrayList<>();
            for (var ingredient : inventory) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("name", ingredient.name());
                value.put("quantity", ingredient.quantity());
                value.put("unit", ingredient.unit());
                inventoryPayload.add(value);
            }
            request.putPOJO("inventory", inventoryPayload);
            Map<String, Object> preferencePayload = new LinkedHashMap<>();
            preferencePayload.put("tastes", tastes);
            preferencePayload.put("cuisines", cuisines);
            preferencePayload.put("goal", goal);
            preferencePayload.put("calorieTarget", calorieTarget);
            request.putPOJO("preferences", preferencePayload);
            request.putPOJO("candidates", candidates);
            messages.addObject().put("role", "user").put("content", mapper.writeValueAsString(request));
            JsonNode response = client.postJson("recipe-ai", endpoint(properties.getBaseUrl(), "/chat/completions"), properties.getApiKey(), body, properties.getTimeout());
            String content = response.path("choices").path(0).path("message").path("content").asText("");
            JsonNode output = mapper.readTree(content);
            Set<UUID> allowed = new HashSet<>(fallback);
            candidates.forEach(candidate -> allowed.add(candidate.id()));
            List<UUID> ids = new ArrayList<>();
            JsonNode values = output.path("recipeIds");
            if (values.isArray()) for (JsonNode value : values) {
                try { UUID id = UUID.fromString(value.asText()); if (allowed.contains(id) && !ids.contains(id)) ids.add(id); }
                catch (IllegalArgumentException ignored) { }
                if (ids.size() == count) break;
            }
            if (ids.isEmpty()) throw new IllegalStateException("AI returned no valid recipe IDs");
            while (ids.size() < count) for (UUID id : fallback) { if (!ids.contains(id)) ids.add(id); if (ids.size() == count) break; }
            String rationale = output.path("rationale").asText("").trim();
            if (rationale.length() > 120) rationale = rationale.substring(0, 120);
            return new GeneratedRecipes(ids, rationale.isBlank() ? "已结合你的库存和饮食偏好优化排序" : rationale, properties.getModelName(), false);
        } catch (Exception exception) {
            return new GeneratedRecipes(fallback, "AI 暂不可用，已使用库存和偏好规则推荐", "rules-v2", true);
        }
    }

    private static String endpoint(String base, String path) {
        String value = base.replaceAll("/$", "");
        return value.endsWith(path) ? value : value + path;
    }
}
