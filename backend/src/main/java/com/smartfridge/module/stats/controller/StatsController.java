package com.smartfridge.module.stats.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/consumption")
    public Result<StatsService.ConsumptionStat> consumption(
            @RequestParam(defaultValue = "month") String period) {
        return Result.ok(statsService.consumption(period));
    }

    @GetMapping("/summary")
    public Result<StatsService.SummaryVO> summary() {
        return Result.ok(statsService.summary());
    }
}
