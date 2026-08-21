package com.xianzhi.fridge.recipe.api;

import com.xianzhi.fridge.recipe.application.RecipeService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class RecipeController {
    private final RecipeService recipes;public RecipeController(RecipeService recipes){this.recipes=recipes;}
    @GetMapping("/recipes") public ApiEnvelope<List<RecipeContracts.RecipeView>> list(@AuthenticationPrincipal UserPrincipal p,
            @RequestParam(required=false) Integer maxCookMinutes,@RequestParam(required=false) String taste,@RequestParam(required=false) String cuisine,
            @RequestParam(required=false) BigDecimal maxCalories,@RequestParam(required=false) String goal,@RequestParam(required=false) String availability,
            @RequestParam(required=false) String query,@RequestParam(required=false) String sort){return ApiEnvelope.ok(recipes.list(p.userId(),maxCookMinutes,taste,cuisine,maxCalories,goal,availability,query));}
    @GetMapping("/recipes/{id}") public ApiEnvelope<RecipeContracts.RecipeView> get(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id){return ApiEnvelope.ok(recipes.get(p.userId(),id));}
    @PostMapping("/recipes/generate") public ApiEnvelope<Map<String,Object>> generate(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody RecipeContracts.GenerateRequest r){var result=recipes.generateResult(p.userId(),r);return ApiEnvelope.ok(Map.of("recipes",result.recipes(),"fallback",result.fallback(),"model",result.model(),"rationale",result.rationale()));}
    @PostMapping("/recipes/recommend") public ApiEnvelope<Map<String,Object>> recommend(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody RecipeContracts.GenerateRequest r){var result=recipes.recommendResult(p.userId(),r);return ApiEnvelope.ok(Map.of("recipes",result.recipes(),"fallback",result.fallback(),"model",result.model(),"rationale",result.rationale()));}
    @PostMapping("/recipes/search-web") public ApiEnvelope<RecipeContracts.WebSearchResponse> searchWeb(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody RecipeContracts.GenerateRequest r){return ApiEnvelope.ok(recipes.searchWeb(p.userId(),r));}
    @PostMapping("/recipes/generated/publish") public ApiEnvelope<List<RecipeContracts.RecipeView>> publishGenerated(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.GeneratedRecipeSelection r){return ApiEnvelope.ok(recipes.publishGenerated(p.userId(),key,r));}
    @PostMapping("/recipes/generated/discard") public ApiEnvelope<Map<String,Integer>> discardGenerated(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.GeneratedRecipeSelection r){return ApiEnvelope.ok(Map.of("discarded",recipes.discardGenerated(p.userId(),r.recipeIds())));}
    @PostMapping("/recipe-synthesis/match") public ApiEnvelope<RecipeContracts.MatchView> match(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody RecipeContracts.MatchRequest r){return ApiEnvelope.ok(recipes.match(p.userId(),r));}
    @PostMapping("/recipes/{id}/scale") public ApiEnvelope<RecipeContracts.ScaleView> scale(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@Valid @RequestBody RecipeContracts.ScaleRequest r){return ApiEnvelope.ok(recipes.scale(p.userId(),id,r));}
    @PutMapping("/recipes/{id}/bookmark") public ApiEnvelope<Map<String,Boolean>> bookmark(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){recipes.bookmark(p.userId(),id);return ApiEnvelope.ok(Map.of("bookmarked",true));}
    @DeleteMapping("/recipes/{id}/bookmark") public ApiEnvelope<Map<String,Boolean>> unbookmark(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){recipes.unbookmark(p.userId(),id);return ApiEnvelope.ok(Map.of("bookmarked",false));}
    @GetMapping("/fridges/{fridgeId}/recipe-plans") public ApiEnvelope<List<RecipeContracts.PlannedRecipeView>> plans(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID fridgeId){return ApiEnvelope.ok(recipes.plans(p.userId(),fridgeId));}
    @PostMapping("/fridges/{fridgeId}/recipe-plans") public ApiEnvelope<RecipeContracts.PlannedRecipeView> addPlan(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID fridgeId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.PlanCreateRequest r){return ApiEnvelope.ok(recipes.addPlan(p.userId(),fridgeId,r));}
    @PatchMapping("/recipe-plans/{id}") public ApiEnvelope<RecipeContracts.PlannedRecipeView> updatePlan(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.PlanUpdateRequest r){return ApiEnvelope.ok(recipes.updatePlan(p.userId(),id,r));}
    @DeleteMapping("/recipe-plans/{id}") public ApiEnvelope<Void> deletePlan(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){recipes.deletePlan(p.userId(),id);return ApiEnvelope.ok(null);}
    @PostMapping("/recipes/{id}/cook") public ApiEnvelope<RecipeContracts.CookView> cook(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.CookRequest r){return ApiEnvelope.ok(recipes.cook(p.userId(),id,key,r));}
}
