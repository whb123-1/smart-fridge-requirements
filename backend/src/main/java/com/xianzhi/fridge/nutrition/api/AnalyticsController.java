package com.xianzhi.fridge.nutrition.api;

import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.nutrition.application.MealService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import java.time.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final MealService meals;private final AppUserRepository users;public AnalyticsController(MealService meals,AppUserRepository users){this.meals=meals;this.users=users;}
    @GetMapping("/consumption") public ApiEnvelope<MealContracts.ConsumptionView> consumption(@AuthenticationPrincipal UserPrincipal p,@RequestParam(defaultValue="week") String period){return ApiEnvelope.ok(meals.consumption(p.userId(),period));}
    @GetMapping("/diet") public ApiEnvelope<MealContracts.DietView> diet(@AuthenticationPrincipal UserPrincipal p,@RequestParam(required=false) LocalDate date){ZoneId zone=ZoneId.of(users.findById(p.userId()).orElseThrow().getTimezone());return ApiEnvelope.ok(meals.diet(p.userId(),date==null?LocalDate.now(zone):date,zone));}
}
