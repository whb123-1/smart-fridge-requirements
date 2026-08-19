package com.xianzhi.fridge.recipe.api;

import com.xianzhi.fridge.recipe.application.AdminRecipeService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController @RequestMapping("/api/v1/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminRecipeController {
    private final AdminRecipeService service;public AdminRecipeController(AdminRecipeService service){this.service=service;}
    @GetMapping("/recipe-sources") public ApiEnvelope<List<RecipeContracts.SourceView>> sources(){return ApiEnvelope.ok(service.sources());}
    @PostMapping("/recipe-sources") public ApiEnvelope<RecipeContracts.SourceView> create(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.SourceRequest r){return ApiEnvelope.ok(service.create(p.userId(),key,r));}
    @PatchMapping("/recipe-sources/{id}") public ApiEnvelope<RecipeContracts.SourceView> update(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.SourceRequest r){return ApiEnvelope.ok(service.update(p.userId(),id,key,r));}
    @PostMapping("/recipe-import-jobs") public ResponseEntity<ApiEnvelope<RecipeContracts.ImportJobView>> importJob(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecipeContracts.ImportRequest r){return ResponseEntity.accepted().body(ApiEnvelope.ok(service.importJob(p.userId(),key,r)));}
    @GetMapping("/recipe-import-jobs") public ApiEnvelope<RecipeContracts.PageView<RecipeContracts.ImportJobView>> imports(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiEnvelope.ok(service.imports(status,page,size));}
    @GetMapping("/recipe-import-jobs/{id}") public ApiEnvelope<RecipeContracts.ImportJobView> getImport(@PathVariable UUID id){return ApiEnvelope.ok(service.getImport(id));}
    @PostMapping("/recipe-import-jobs/{id}/retry") public ResponseEntity<ApiEnvelope<RecipeContracts.ImportJobView>> retryImport(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key){return ResponseEntity.accepted().body(ApiEnvelope.ok(service.retryImport(p.userId(),id,key)));}
    @GetMapping("/search-index") public ApiEnvelope<RecipeContracts.SearchIndexView> searchIndex(){return ApiEnvelope.ok(service.searchIndex());}
    @PostMapping("/search-index/rebuild") public ResponseEntity<ApiEnvelope<RecipeContracts.IndexRebuildJobView>> rebuild(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("Idempotency-Key") String key){return ResponseEntity.accepted().body(ApiEnvelope.ok(service.rebuild(p.userId(),key)));}
}
