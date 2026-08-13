package com.smartfridge.module.user.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.user.entity.UserPreference;
import com.smartfridge.module.user.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/preference")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    public Result<UserPreference> get() {
        return Result.ok(preferenceService.get());
    }

    @PutMapping
    public Result<UserPreference> update(@RequestBody PreferenceService.PreferenceReq req) {
        return Result.ok(preferenceService.update(req));
    }
}
