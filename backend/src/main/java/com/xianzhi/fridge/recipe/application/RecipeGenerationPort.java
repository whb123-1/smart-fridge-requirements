package com.xianzhi.fridge.recipe.application;

import com.xianzhi.fridge.recipe.api.RecipeContracts;
import java.util.List;
import java.util.UUID;

/** Optional structured AI ranking for recipe candidates. The database remains the source of truth. */
public interface RecipeGenerationPort {
    GeneratedRecipes rank(String prompt, List<Candidate> candidates, List<RecipeContracts.IngredientInput> inventory,
                          List<String> tastes, List<String> cuisines, String goal, Integer calorieTarget, int count);

    record Candidate(UUID id, String title, String summary, String cuisine, String taste, String goal,
                     int cookMinutes, List<String> ingredients, int inventoryMatches, int missingCount) { }
    record GeneratedRecipes(List<UUID> recipeIds, String rationale, String model, boolean fallback) { }
}
