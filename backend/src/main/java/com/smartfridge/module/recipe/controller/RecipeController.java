package com.smartfridge.module.recipe.controller;

import com.smartfridge.common.Result;
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

    @PostMapping("/generate")
    public Result<RecipeService.RecipeDetailVO> generate(@RequestBody RecipeService.GenerateReq req) {
        return Result.ok(recipeService.generate(req));
    }

    @PostMapping("/check-selected")
    public Result<RecipeService.CheckResult> checkSelected(
            @RequestBody RecipeService.CheckReq req) {
        return Result.ok(recipeService.checkSelected(req));
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
}
