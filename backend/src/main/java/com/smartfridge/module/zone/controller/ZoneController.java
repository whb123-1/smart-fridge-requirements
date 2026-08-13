package com.smartfridge.module.zone.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.zone.entity.FridgeZone;
import com.smartfridge.module.zone.entity.ZoneRecord;
import com.smartfridge.module.zone.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    public Result<List<ZoneService.ZoneVO>> list() {
        return Result.ok(zoneService.list());
    }

    @GetMapping("/alerts")
    public Result<List<ZoneService.ZoneAlert>> alerts() {
        return Result.ok(zoneService.alerts());
    }

    @PostMapping
    public Result<FridgeZone> create(@RequestBody ZoneService.ZoneReq req) {
        return Result.ok(zoneService.create(req));
    }

    @PutMapping("/{id}")
    public Result<FridgeZone> update(@PathVariable Long id, @RequestBody ZoneService.ZoneReq req) {
        return Result.ok(zoneService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        zoneService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/records")
    public Result<ZoneRecord> record(@PathVariable Long id, @RequestBody ZoneService.RecordReq req) {
        return Result.ok(zoneService.record(id, req));
    }

    @GetMapping("/{id}/records")
    public Result<List<ZoneRecord>> records(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(zoneService.records(id, from, to));
    }
}
