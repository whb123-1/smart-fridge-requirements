package com.xianzhi.fridge.nutrition.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.nutrition.api.MealContracts;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import com.xianzhi.fridge.shared.web.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenAiMealNutritionEstimator implements MealNutritionEstimator {
    private final AssistantProperties properties;
    private final ExternalProviderClient client;
    private final ObjectMapper mapper;

    public OpenAiMealNutritionEstimator(AssistantProperties properties, ExternalProviderClient client, ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public MealContracts.NutritionView estimate(MealContracts.EstimateRequest request) {
        if (!configured()) {
            throw unavailable("AI nutrition provider is not configured");
        }
        try {
            var body = mapper.createObjectNode();
            body.put("model", properties.getModelName());
            body.put("temperature", 0.1);
            body.putObject("response_format").put("type", "json_object");
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", """
                    你是食物营养估算器。根据菜品名称和用户给出的份量，估算这一餐的总热量与三大营养素。
                    amount 为空时按一份常见成品份量估算；有 amount 时必须严格按对应 unit 估算。
                    只能输出 JSON：{"calories":数字,"protein":数字,"fat":数字,"carbs":数字,"rationale":"不超过80字的中文估算依据"}。
                    数值单位依次为千卡、克、克、克，必须是非负有限数字。不要输出区间或 Markdown。
                    """);
            var input = mapper.createObjectNode();
            input.put("dishName", request.dishName().trim());
            if (request.amount() == null) input.putNull("amount"); else input.put("amount", request.amount());
            input.put("unit", request.unit() == null || request.unit().isBlank() ? "serving" : request.unit().trim());
            messages.addObject().put("role", "user").put("content", mapper.writeValueAsString(input));
            JsonNode response = client.postJson("nutrition-ai", endpoint(properties.getBaseUrl(), "/chat/completions"),
                    properties.getApiKey(), body, properties.getTimeout());
            String content = response.path("choices").path(0).path("message").path("content").asText("");
            JsonNode output = mapper.readTree(content);
            BigDecimal calories = number(output, "calories", BigDecimal.ONE, BigDecimal.valueOf(10000));
            BigDecimal protein = number(output, "protein", BigDecimal.ZERO, BigDecimal.valueOf(1000));
            BigDecimal fat = number(output, "fat", BigDecimal.ZERO, BigDecimal.valueOf(1000));
            BigDecimal carbs = number(output, "carbs", BigDecimal.ZERO, BigDecimal.valueOf(2000));
            String source = "AI_ESTIMATE:" + properties.getModelName();
            if (source.length() > 64) source = source.substring(0, 64);
            return new MealContracts.NutritionView(calories, protein, fat, carbs, true, source, MealService.DISCLAIMER);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable("AI nutrition provider is temporarily unavailable");
        }
    }

    private boolean configured() {
        return properties.isExternalCallsEnabled()
                && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                && properties.getApiKey() != null && !properties.getApiKey().isBlank()
                && properties.getModelName() != null && !properties.getModelName().isBlank();
    }

    private static BigDecimal number(JsonNode output, String field, BigDecimal minimum, BigDecimal maximum) {
        JsonNode value = output.path(field);
        if (!value.isNumber()) throw new IllegalStateException("AI nutrition output is missing " + field);
        BigDecimal number = value.decimalValue().stripTrailingZeros();
        if (number.compareTo(minimum) < 0 || number.compareTo(maximum) > 0) {
            throw new IllegalStateException("AI nutrition output is outside the allowed range");
        }
        return number;
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NUTRITION_UNAVAILABLE", message);
    }

    private static String endpoint(String base, String path) {
        String value = base.replaceAll("/$", "");
        return value.endsWith(path) ? value : value + path;
    }
}
