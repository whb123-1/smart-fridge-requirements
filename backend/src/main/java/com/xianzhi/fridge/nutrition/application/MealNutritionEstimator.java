package com.xianzhi.fridge.nutrition.application;

import com.xianzhi.fridge.nutrition.api.MealContracts;

public interface MealNutritionEstimator {
    MealContracts.NutritionView estimate(MealContracts.EstimateRequest request);
}
