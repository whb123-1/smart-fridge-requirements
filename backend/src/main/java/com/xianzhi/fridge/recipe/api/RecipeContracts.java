package com.xianzhi.fridge.recipe.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecipeContracts {
    private RecipeContracts() { }
    public record Nutrition(BigDecimal calories,BigDecimal protein,BigDecimal fat,BigDecimal carbs) { }
    public record Component(UUID id,String name,String role,BigDecimal quantity,String unit,String scalingRule,
                            BigDecimal minimumQuantity,BigDecimal maximumQuantity) { }
    public record DetailedStep(int number,String title,String instruction,String duration,String heat,String checkpoint) { }
    public record RecipeView(UUID id,String name,String description,String cuisine,String taste,String goal,int cookMinutes,
                             BigDecimal servings,Nutrition total,Nutrition perServing,List<Component> ingredients,
                             List<String> steps,List<DetailedStep> detailedSteps,List<String> utensils,String nutritionSource,
                             boolean bookmarked,String source,String sourceVersion,String attribution,
                             String imageUrl,String imageSourceUrl,String imageAttribution,
                             String availability,List<String> missing,List<String> validationWarnings) { }
    public record GenerateRequest(UUID fridgeId,@Size(max=500) String prompt,List<@Valid IngredientInput> inventory,@Min(1) @Max(3) Integer count) { }
    public record GeneratedRecipeSelection(@NotEmpty @Size(max=3) List<@NotNull UUID> recipeIds) { }
    public record IngredientInput(UUID batchId,@NotBlank String name,BigDecimal quantity,String unit) { }
    public record MatchRequest(@NotEmpty @Size(max=4) List<@Valid IngredientInput> ingredients) { }
    public record MatchView(UUID synthesisId,List<RecipeView> recipes,List<String> matched,List<String> unmatched,List<String> suggestions,String source) { }
    public record ScaleRequest(@NotNull UUID primaryComponentId,@NotNull @DecimalMin("0.001") BigDecimal quantity,
                               @NotBlank String unit,@NotNull @DecimalMin("0.1") BigDecimal servings) { }
    public record ScaleView(UUID recipeId,BigDecimal servings,List<Component> components,Nutrition total,Nutrition perServing) { }
    public record Consumption(@NotNull UUID batchId,@NotNull @DecimalMin("0.001") BigDecimal quantity,@NotBlank String unit) { }
    public record CookRequest(@NotNull @DecimalMin("0.1") BigDecimal servings,@NotEmpty List<@Valid Consumption> consumptions,
                              boolean recordMeal,Instant mealAt,UUID synthesisId) { }
    public record CookView(UUID recipeId,List<UUID> transactionBatchIds,UUID mealId) { }
    public record PlanCreateRequest(@NotNull UUID recipeId,@NotNull @DecimalMin("0.1") BigDecimal servings) { }
    public record PlanUpdateRequest(@NotNull @DecimalMin("0.1") BigDecimal servings) { }
    public record PlannedRecipeView(UUID id,UUID fridgeId,UUID recipeId,BigDecimal servings,Instant createdAt,RecipeView recipe) { }
    public record SourceRequest(@NotBlank @Size(max=160) String name,@NotBlank String sourceType,@NotBlank String licenseCode,
                                @NotBlank @Size(max=500) String attributionText,@NotBlank String allowedUse,@NotBlank String sourceVersion,Boolean enabled) { }
    public record SourceView(UUID id,String name,String sourceType,String licenseCode,String attributionText,String allowedUse,String sourceVersion,boolean enabled) { }
    public record ImportRequest(@NotNull UUID sourceId,@NotNull Object payload) { }
    public record ImportJobView(UUID id,UUID sourceId,String status,String checksum,int importedCount,int skippedCount,
                                int errorCount,int attemptCount,String lastError,Instant createdAt,Instant startedAt,
                                Instant completedAt) { }
    public record PageView<T>(List<T> items,long total,int page,int size,int totalPages) { }
    public record IndexRebuildJobView(UUID id,String status,String collectionName,String embeddingModelVersion,
                                      int totalCount,int processedCount,int failureCount,String lastError,
                                      Instant createdAt,Instant startedAt,Instant completedAt) { }
    public record SearchIndexView(boolean vectorEnabled,String activeCollection,String embeddingModelVersion,
                                  long readyCount,long mysqlOnlyCount,long failedCount,
                                  IndexRebuildJobView latestRebuild) { }
}
