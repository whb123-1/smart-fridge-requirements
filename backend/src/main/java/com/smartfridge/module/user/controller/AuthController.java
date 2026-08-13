package com.smartfridge.module.user.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<AuthService.UserVO> register(@Valid @RequestBody AuthService.RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/login")
    public Result<AuthService.LoginResp> login(@Valid @RequestBody AuthService.LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @GetMapping("/me")
    public Result<AuthService.UserVO> me() {
        return Result.ok(authService.me());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态，前端丢弃 token 即可
        return Result.ok();
    }
}
