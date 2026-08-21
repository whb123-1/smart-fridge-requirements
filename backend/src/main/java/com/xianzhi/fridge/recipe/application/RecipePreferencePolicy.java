package com.xianzhi.fridge.recipe.application;

import com.xianzhi.fridge.identity.api.PreferenceContracts;
import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RecipePreferencePolicy {
    public boolean allows(RecipeStore.Row recipe, List<RecipeContracts.Component> ingredients,
                          PreferenceContracts.View preferences) {
        if (!allowsSafety(ingredients, preferences)) return false;
        return softWarnings(recipe, preferences).isEmpty();
    }

    public boolean allowsSafety(List<RecipeContracts.Component> ingredients, PreferenceContracts.View preferences) {
        List<String> exclusions = new ArrayList<>(preferences.allergies());
        exclusions.addAll(preferences.dislikes());
        return exclusions.stream().noneMatch(excluded -> ingredients.stream()
                .anyMatch(ingredient -> similar(excluded, ingredient.name())));
    }

    public List<String> softWarnings(RecipeStore.Row recipe, PreferenceContracts.View preferences) {
        List<String> warnings = new ArrayList<>();
        String searchable = String.join(" ", values(recipe.title(), recipe.summary(), recipe.taste(), recipe.cuisine(), recipe.goal()));
        if (!preferences.tastes().isEmpty() && preferences.tastes().stream().noneMatch(value -> tasteAllows(value, recipe, searchable))) warnings.add("该菜谱与已设置的口味偏好可能冲突");
        if (!preferences.cuisines().isEmpty() && preferences.cuisines().stream().noneMatch(value -> similar(value, searchable))) warnings.add("该菜谱不属于已设置的偏好菜系");
        if (!goalAllows(recipe, preferences.dietaryGoal(), preferences.calorieTarget())) warnings.add("该菜谱与当前饮食或热量目标可能冲突");
        return warnings;
    }

    private static boolean goalAllows(RecipeStore.Row recipe, String goal, Integer calorieTarget) {
        if (goal == null || goal.isBlank()) return true;
        BigDecimal servings = recipe.servings() == null || recipe.servings().signum() <= 0 ? BigDecimal.ONE : recipe.servings();
        BigDecimal calories = recipe.calories() == null ? null : recipe.calories().divide(servings, 2, RoundingMode.HALF_UP);
        BigDecimal protein = recipe.protein() == null ? null : recipe.protein().divide(servings, 2, RoundingMode.HALF_UP);
        if (goal.contains("减脂")) return calories == null || calories.compareTo(BigDecimal.valueOf(500)) <= 0;
        if (goal.contains("控制热量")) {
            BigDecimal limit = BigDecimal.valueOf(calorieTarget == null ? 600 : Math.max(300, calorieTarget / 3));
            return calories == null || calories.compareTo(limit) <= 0;
        }
        if (goal.contains("增肌")) return protein == null || protein.compareTo(BigDecimal.valueOf(25)) >= 0
                || similar("高蛋白", String.join(" ", values(recipe.goal(), recipe.summary())));
        return similar(goal, String.join(" ", values(recipe.goal(), recipe.summary())));
    }

    private static boolean tasteAllows(String taste, RecipeStore.Row recipe, String searchable) {
        if (similar(taste, searchable)) return true;
        BigDecimal servings = recipe.servings() == null || recipe.servings().signum() <= 0 ? BigDecimal.ONE : recipe.servings();
        BigDecimal fat = recipe.fat() == null ? null : recipe.fat().divide(servings, 2, RoundingMode.HALF_UP);
        BigDecimal carbs = recipe.carbs() == null ? null : recipe.carbs().divide(servings, 2, RoundingMode.HALF_UP);
        if (taste.contains("少油")) return fat != null && fat.compareTo(BigDecimal.valueOf(20)) <= 0;
        if (taste.contains("少糖")) return carbs != null && carbs.compareTo(BigDecimal.valueOf(45)) <= 0;
        if (taste.contains("清淡")) return !searchable.matches(".*(中辣|重辣|香辣|油炸|红烧).*" )
                && (fat == null || fat.compareTo(BigDecimal.valueOf(22)) <= 0);
        if (taste.contains("低盐")) return !searchable.matches(".*(咸香|咸辣|腌制|酱香).*" );
        return false;
    }

    static boolean similar(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isBlank() && !b.isBlank() && (a.contains(b) || b.contains(a));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static List<String> values(String... values) {
        List<String> output = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) output.add(value);
        return output;
    }
}
