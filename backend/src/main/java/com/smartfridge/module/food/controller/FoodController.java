package com.smartfridge.module.food.controller;

import com.smartfridge.common.PageResult;
import com.smartfridge.common.Result;
import com.smartfridge.module.food.entity.FoodCategory;
import com.smartfridge.module.food.entity.FoodEstimate;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.service.FoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @GetMapping
    public Result<PageResult<FoodService.FoodVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemType) {
        return Result.ok(foodService.list(page, size, keyword, categoryId, zoneId, status, itemType));
    }

    @GetMapping("/categories")
    public Result<List<FoodCategory>> categories() {
        return Result.ok(foodService.categories());
    }

    @GetMapping("/estimates")
    public Result<List<FoodEstimate>> estimates() {
        return Result.ok(foodService.estimates());
    }

    @PostMapping
    public Result<FoodItem> add(@RequestBody FoodService.FoodReq req) {
        return Result.ok(foodService.add(req));
    }

    @PutMapping("/{id}")
    public Result<FoodItem> update(@PathVariable Long id, @RequestBody FoodService.FoodReq req) {
        return Result.ok(foodService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        foodService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/consume")
    public Result<FoodItem> consume(@PathVariable Long id, @RequestBody FoodService.ConsumeReq req) {
        return Result.ok(foodService.consume(id, req));
    }

    @PostMapping("/{id}/expire")
    public Result<FoodItem> expire(@PathVariable Long id) {
        return Result.ok(foodService.markExpired(id));
    }

    @PostMapping("/{id}/discard")
    public Result<FoodItem> discard(@PathVariable Long id,
                                    @RequestParam(required = false) String remark) {
        return Result.ok(foodService.discard(id, remark));
    }
}
