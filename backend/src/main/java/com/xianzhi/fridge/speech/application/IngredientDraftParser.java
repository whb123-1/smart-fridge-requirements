package com.xianzhi.fridge.speech.application;

import com.xianzhi.fridge.inventory.domain.FoodCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IngredientDraftParser {
    public Map<String, Object> parse(String transcript, UUID fridgeId) {
        String text = transcript == null ? "" : transcript.trim();
        BigDecimal quantity = BigDecimal.ONE;
        java.util.regex.Matcher amount = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(text);
        if (amount.find()) quantity = new BigDecimal(amount.group(1));
        String name = text.replaceAll("\\d+(?:\\.\\d+)?", "")
                .replace("个", "").replace("盒", "").replace("克", "").replace("毫升", "").trim();
        if (name.isBlank()) name = text.isBlank() ? "未识别食材" : text;
        String unit = text.contains("克") ? "g" : text.contains("毫升") ? "ml" : text.contains("盒") ? "box" : "piece";
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("fridgeId", fridgeId);
        draft.put("name", name);
        draft.put("category", FoodCategory.OTHER.name());
        draft.put("quantity", quantity);
        draft.put("unit", unit);
        draft.put("storedAt", Instant.now());
        return draft;
    }
}
