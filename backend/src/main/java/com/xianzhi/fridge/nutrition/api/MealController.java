package com.xianzhi.fridge.nutrition.api;

import com.xianzhi.fridge.nutrition.application.MealService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/meals")
public class MealController {
    private final MealService meals;public MealController(MealService meals){this.meals=meals;}
    @PostMapping("/estimate-nutrition") public ApiEnvelope<MealContracts.NutritionView> estimate(@Valid @RequestBody MealContracts.EstimateRequest r){return ApiEnvelope.ok(meals.estimate(r));}
    @GetMapping public ApiEnvelope<List<MealContracts.MealView>> list(@AuthenticationPrincipal UserPrincipal p){return ApiEnvelope.ok(meals.list(p.userId()));}
    @PostMapping public ApiEnvelope<MealContracts.MealView> create(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody MealContracts.CreateRequest r){return ApiEnvelope.ok(meals.create(p.userId(),key,r));}
    @DeleteMapping("/{id}") public ApiEnvelope<Void> delete(@AuthenticationPrincipal UserPrincipal p,@PathVariable java.util.UUID id,@RequestHeader("Idempotency-Key") String key){meals.delete(p.userId(),id,key);return ApiEnvelope.ok(null);}
}
