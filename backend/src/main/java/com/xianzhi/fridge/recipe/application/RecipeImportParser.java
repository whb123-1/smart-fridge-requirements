package com.xianzhi.fridge.recipe.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.shared.domain.Hashing;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class RecipeImportParser {
    private final ObjectMapper mapper;

    public RecipeImportParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<RecipeDocument> parse(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode source = root.isArray() ? root : root.path("recipes");
            if (!source.isArray()) throw new IllegalArgumentException("payload must be an array or contain a recipes array");
            List<RecipeDocument> recipes = new ArrayList<>();
            for (JsonNode node : source) recipes.add(parseRecipe(node));
            return recipes;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("payload is not valid JSON", exception);
        }
    }

    private RecipeDocument parseRecipe(JsonNode node) throws JsonProcessingException {
        String title = requiredText(node, "title", 160);
        int cookMinutes = requiredPositiveInt(node, "cookMinutes");
        BigDecimal servings = requiredPositiveDecimal(node, "servings");
        JsonNode componentNodes = node.path("ingredients");
        if (!componentNodes.isArray() || componentNodes.isEmpty()) throw new IllegalArgumentException(title + ": ingredients are required");
        List<ComponentDocument> components = new ArrayList<>();
        int sort = 0;
        for (JsonNode component : componentNodes) {
            String role = text(component, "role", "PRIMARY").toUpperCase(Locale.ROOT);
            if (!List.of("PRIMARY", "SIDE", "SEASONING").contains(role)) throw new IllegalArgumentException(title + ": invalid ingredient role");
            String scaling = text(component, "scalingRule", "SEASONING".equals(role) ? "BOUNDED" : "LINEAR").toUpperCase(Locale.ROOT);
            if (!List.of("LINEAR", "BOUNDED", "FIXED").contains(scaling)) throw new IllegalArgumentException(title + ": invalid scaling rule");
            components.add(new ComponentDocument(requiredText(component, "name", 120), role,
                    requiredPositiveDecimal(component, "quantity"), requiredText(component, "unit", 24), scaling,
                    decimal(component, "minimumQuantity"), decimal(component, "maximumQuantity"), ++sort));
        }
        JsonNode stepNodes = node.path("steps");
        if (!stepNodes.isArray() || stepNodes.isEmpty()) throw new IllegalArgumentException(title + ": steps are required");
        List<String> steps = new ArrayList<>();
        for (JsonNode step : stepNodes) {
            String value = step.asText("").trim();
            if (value.isBlank()) throw new IllegalArgumentException(title + ": a step is blank");
            steps.add(value);
        }
        JsonNode nutrition = node.path("nutrition");
        String normalized = title.trim().toLowerCase(Locale.ROOT) + "|" + components.stream()
                .sorted(Comparator.comparing(ComponentDocument::name))
                .map(value -> value.name().trim().toLowerCase(Locale.ROOT) + ":" + value.quantity().stripTrailingZeros() + ":" + value.unit())
                .reduce((left, right) -> left + "|" + right).orElse("");
        String snapshot = mapper.writeValueAsString(node);
        return new RecipeDocument(text(node, "sourceRecipeId", null), title, text(node, "summary", null),
                text(node, "cuisine", null), text(node, "taste", null), text(node, "goal", null), cookMinutes,
                servings, decimal(nutrition, "calories"), decimal(nutrition, "protein"), decimal(nutrition, "fat"),
                decimal(nutrition, "carbs"), Hashing.sha256(normalized), snapshot, Hashing.sha256(snapshot), components, steps,
                optionalUrl(node, "imageUrl"), optionalUrl(node, "imageSourceUrl"), optionalAttribution(node));
    }

    private static String requiredText(JsonNode node, String field, int max) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (value.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return value.trim();
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText().trim();
    }

    private static String optionalUrl(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!List.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getHost() == null) {
                throw new IllegalArgumentException(field + " must be an absolute http(s) URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be an absolute http(s) URL", exception);
        }
        if (value.length() > 1024) throw new IllegalArgumentException(field + " exceeds 1024 characters");
        return value;
    }

    private static String optionalAttribution(JsonNode node) {
        String value = text(node, "imageAttribution", null);
        if (value != null && value.length() > 500) throw new IllegalArgumentException("imageAttribution exceeds 500 characters");
        return value;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try { return value.decimalValue(); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(field + " must be numeric"); }
    }

    private static BigDecimal requiredPositiveDecimal(JsonNode node, String field) {
        BigDecimal value = decimal(node, field);
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static int requiredPositiveInt(JsonNode node, String field) {
        int value = node.path(field).asInt(0);
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    public record RecipeDocument(String sourceRecipeId, String title, String summary, String cuisine, String taste,
                                 String goal, int cookMinutes, BigDecimal servings, BigDecimal calories,
                                 BigDecimal protein, BigDecimal fat, BigDecimal carbs, String fingerprint,
                                 String snapshot, String snapshotChecksum, List<ComponentDocument> components,
                                 List<String> steps, String imageUrl, String imageSourceUrl, String imageAttribution) { }

    public record ComponentDocument(String name, String role, BigDecimal quantity, String unit, String scalingRule,
                                    BigDecimal minimumQuantity, BigDecimal maximumQuantity, int sortOrder) { }
}
