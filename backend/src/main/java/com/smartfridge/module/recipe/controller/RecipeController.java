package com.smartfridge.module.recipe.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.recipe.service.AiRecipeService;
import com.smartfridge.module.recipe.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final AiRecipeService aiRecipeService;

    @GetMapping("/recommend")
    public Result<List<RecipeService.RecipeMatchVO>> recommend(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer cookTimeMax,
            @RequestParam(required = false) String taste,
            @RequestParam(required = false) String dietGoal) {
        return Result.ok(recipeService.recommend(keyword, cookTimeMax, taste, dietGoal));
    }

    @GetMapping("/{id}")
    public Result<RecipeService.RecipeDetailVO> detail(@PathVariable Long id) {
        return Result.ok(recipeService.detail(id));
    }

    @PostMapping("/check-selected")
    public Result<RecipeService.CheckResult> checkSelected(
            @RequestBody RecipeService.CheckReq req) {
        return Result.ok(recipeService.checkSelected(req));
    }

    @PostMapping("/ai-recommend")
    public Result<List<AiRecipeService.AiRecommendVO>> aiRecommend(
            @RequestBody(required = false) AiRecommendReq req) {
        return Result.ok(aiRecipeService.recommend(req == null ? null : req.name()));
    }

    @PostMapping("/ai-generate")
    public Result<RecipeService.RecipeDetailVO> aiGenerate(@RequestBody AiGenerateReq req) {
        return Result.ok(aiRecipeService.generate(req.name()));
    }

    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id) {
        recipeService.favorite(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable Long id) {
        recipeService.unfavorite(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recipeService.delete(id);
        return Result.ok();
    }

    @GetMapping("/favorites/list")
    public Result<List<RecipeService.RecipeMatchVO>> favorites() {
        return Result.ok(recipeService.favorites());
    }

    @GetMapping("/history/list")
    public Result<List<RecipeService.HistoryVO>> history() {
        return Result.ok(recipeService.history());
    }

    @PostMapping("/{id}/scale")
    public Result<RecipeService.ScaleResultVO> scale(@PathVariable Long id,
                                                     @RequestBody RecipeService.ScaleReq req) {
        return Result.ok(recipeService.scale(id, req));
    }

    @PostMapping("/{id}/cook")
    public Result<List<RecipeService.CookResultVO>> cook(@PathVariable Long id,
                                                         @RequestBody RecipeService.CookReq req) {
        return Result.ok(recipeService.cook(id, req));
    }

    public record AiGenerateReq(String name) {
    }

    public record AiRecommendReq(String name) {
    }
}
