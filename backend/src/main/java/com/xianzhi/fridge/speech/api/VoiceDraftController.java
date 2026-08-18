package com.xianzhi.fridge.speech.api;

import com.xianzhi.fridge.inventory.api.InventoryContracts;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import com.xianzhi.fridge.speech.application.VoiceDraftService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/inventory/voice-drafts")
public class VoiceDraftController {
    private final VoiceDraftService service; public VoiceDraftController(VoiceDraftService service){this.service=service;}
    @PostMapping(consumes="multipart/form-data")
    public ResponseEntity<ApiEnvelope<VoiceDraftContracts.View>> upload(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID fridgeId, @RequestPart("audio") MultipartFile audio){return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiEnvelope.ok(service.upload(principal.userId(),fridgeId,audio)));}
    @GetMapping("/{id}") public ApiEnvelope<VoiceDraftContracts.View> get(@AuthenticationPrincipal UserPrincipal principal,@PathVariable UUID id){return ApiEnvelope.ok(service.get(principal.userId(),id));}
    @PostMapping("/{id}/confirm") public ApiEnvelope<InventoryContracts.ItemView> confirm(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody VoiceDraftContracts.ConfirmRequest request){return ApiEnvelope.ok(service.confirm(principal.userId(),id,key,request));}
}
