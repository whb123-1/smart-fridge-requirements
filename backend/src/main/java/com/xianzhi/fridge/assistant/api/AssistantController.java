package com.xianzhi.fridge.assistant.api;

import com.xianzhi.fridge.assistant.application.AssistantService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantService assistant;public AssistantController(AssistantService assistant){this.assistant=assistant;}
    @GetMapping("/briefing") public ApiEnvelope<AssistantContracts.Briefing> briefing(@AuthenticationPrincipal UserPrincipal p){return ApiEnvelope.ok(assistant.briefing(p.userId()));}
    @PostMapping("/conversations") public ApiEnvelope<AssistantContracts.ConversationView> create(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AssistantContracts.CreateConversationRequest r){return ApiEnvelope.ok(assistant.create(p.userId(),key,r));}
    @PostMapping("/conversations/{id}/messages") public ApiEnvelope<AssistantContracts.MessageResponse> message(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AssistantContracts.MessageRequest r){return ApiEnvelope.ok(assistant.message(p.userId(),id,key,r));}
    @PostMapping("/action-proposals/{id}/confirm") public ApiEnvelope<AssistantContracts.ActionResult> confirm(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return ApiEnvelope.ok(assistant.confirm(p.userId(),id,key));}
    @PostMapping("/action-proposals/{id}/dismiss") public ApiEnvelope<AssistantContracts.ActionResult> dismiss(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return ApiEnvelope.ok(assistant.dismiss(p.userId(),id,key));}
}
