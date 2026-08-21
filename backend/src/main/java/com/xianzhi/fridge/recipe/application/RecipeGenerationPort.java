package com.xianzhi.fridge.recipe.application;

import com.xianzhi.fridge.recipe.api.RecipeContracts;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Optional structured AI ranking for recipe candidates. The database remains the source of truth. */
public interface RecipeGenerationPort {
    GeneratedRecipes rank(String prompt, List<Candidate> candidates, List<RecipeContracts.IngredientInput> inventory,
                          List<String> tastes, List<String> cuisines, String goal, Integer calorieTarget, int count);

    Discovery discover(String prompt, List<String> existingTitles, List<RecipeContracts.IngredientInput> inventory,
                       List<String> tastes, List<String> cuisines, List<String> exclusions,
                       String goal, Integer calorieTarget, int count);

    record Candidate(UUID id, String title, String summary, String cuisine, String taste, String goal,
                     int cookMinutes, List<String> ingredients, int inventoryMatches, int missingCount) { }
    record GeneratedRecipes(List<UUID> recipeIds, String rationale, String model, boolean fallback) { }
    record DraftIngredient(String name, String role, BigDecimal quantity, String unit, String scalingRule) { }
    record DraftNutrition(BigDecimal calories, BigDecimal protein, BigDecimal fat, BigDecimal carbs) { }
    record Draft(String title, String summary, String cuisine, String taste, String goal, int cookMinutes,
                 BigDecimal servings, DraftNutrition nutrition, List<DraftIngredient> ingredients,
                 List<String> steps) { }
    record Discovery(List<Draft> recipes, String rationale, String model, boolean fallback) { }
}
