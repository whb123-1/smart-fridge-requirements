package com.xianzhi.fridge.nutrition.api;

import com.xianzhi.fridge.nutrition.application.MealService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final MealService meals;public AnalyticsController(MealService meals){this.meals=meals;}
    @GetMapping("/consumption") public ApiEnvelope<MealContracts.ConsumptionView> consumption(@AuthenticationPrincipal UserPrincipal p,@RequestParam(defaultValue="week") String period){return ApiEnvelope.ok(meals.consumption(p.userId(),period));}
    @GetMapping("/diet") public ApiEnvelope<MealContracts.DietView> diet(@AuthenticationPrincipal UserPrincipal p,@RequestParam(required=false) LocalDate date){return ApiEnvelope.ok(meals.diet(p.userId(),date));}
}
