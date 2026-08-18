package com.xianzhi.fridge.nutrition.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MealContracts {
    private MealContracts() { }
    public record EstimateRequest(@NotBlank @Size(max=160) String dishName,@DecimalMin("0.001") BigDecimal amount,@Size(max=24) String unit) { }
    public record NutritionView(BigDecimal calories,BigDecimal protein,BigDecimal fat,BigDecimal carbs,boolean estimated,String source,String disclaimer) { }
    public record CreateRequest(UUID recipeId,@NotNull Instant mealAt,@Size(max=24) String mealType,@NotBlank @Size(max=160) String name,
                                @NotNull @DecimalMin("0.1") BigDecimal servings,BigDecimal calories,BigDecimal protein,BigDecimal fat,BigDecimal carbs,
                                Boolean estimated,String nutritionSource) { }
    public record MealView(UUID id,UUID recipeId,Instant mealAt,String mealType,String name,BigDecimal servings,NutritionView nutrition) { }
    public record ConsumptionView(String period,BigDecimal consumed,BigDecimal discarded,BigDecimal expired,String unit) { }
    public record DietView(Instant from,Instant to,BigDecimal calories,BigDecimal protein,BigDecimal fat,BigDecimal carbs,int mealCount,String disclaimer) { }
}
