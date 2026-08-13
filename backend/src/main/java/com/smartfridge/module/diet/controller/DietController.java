package com.smartfridge.module.diet.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.diet.entity.DietRecord;
import com.smartfridge.module.diet.service.DietService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/diet")
@RequiredArgsConstructor
public class DietController {

    private final DietService dietService;

    @PostMapping("/records")
    public Result<DietRecord> add(@RequestBody DietService.DietReq req) {
        return Result.ok(dietService.add(req));
    }

    @DeleteMapping("/records/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        dietService.remove(id);
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<List<DietRecord>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dietService.list(date));
    }

    @GetMapping("/summary")
    public Result<DietService.DailySummary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dietService.summary(date));
    }
}
