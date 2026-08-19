package com.xianzhi.fridge.identity.api;

import com.xianzhi.fridge.identity.application.AdminUserService;
import com.xianzhi.fridge.identity.domain.UserRole;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service){this.service=service;}
    @GetMapping public ApiEnvelope<AdminUserContracts.PageView<AdminUserContracts.UserView>> list(
            @RequestParam(required=false) String query,@RequestParam(required=false) UserRole role,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size){return ApiEnvelope.ok(service.list(query,role,status,page,size));}
    @GetMapping("/{id}") public ApiEnvelope<AdminUserContracts.UserView> get(@PathVariable UUID id){return ApiEnvelope.ok(service.get(id));}
    @GetMapping("/{id}/audit-logs") public ApiEnvelope<AdminUserContracts.PageView<AdminUserContracts.AuditView>> audits(
            @PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiEnvelope.ok(service.auditLogs(id,page,size));}
    @PatchMapping("/{id}/status") public ApiEnvelope<AdminUserContracts.ActionView> status(
            @AuthenticationPrincipal UserPrincipal actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AdminUserContracts.StatusRequest request){return ApiEnvelope.ok(service.status(actor.userId(),id,key,request));}
    @PatchMapping("/{id}/role") public ApiEnvelope<AdminUserContracts.ActionView> role(
            @AuthenticationPrincipal UserPrincipal actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AdminUserContracts.RoleRequest request){return ApiEnvelope.ok(service.role(actor.userId(),id,key,request));}
    @PostMapping("/{id}/sessions/revoke") public ApiEnvelope<AdminUserContracts.ActionView> revoke(
            @AuthenticationPrincipal UserPrincipal actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return ApiEnvelope.ok(service.revokeSessions(actor.userId(),id,key));}
    @PostMapping("/{id}/password-reset") public ApiEnvelope<AdminUserContracts.TemporaryPasswordView> password(
            @AuthenticationPrincipal UserPrincipal actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return ApiEnvelope.ok(service.resetPassword(actor.userId(),id,key));}
    @DeleteMapping("/{id}") public ApiEnvelope<AdminUserContracts.ActionView> delete(
            @AuthenticationPrincipal UserPrincipal actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return ApiEnvelope.ok(service.delete(actor.userId(),id,key));}
    @PostMapping("/{id}/restore") public ApiEnvelope<AdminUserContracts.ActionView> restore(
            @AuthenticationPrincipal UserPrincipal actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return ApiEnvelope.ok(service.restore(actor.userId(),id,key));}
}
