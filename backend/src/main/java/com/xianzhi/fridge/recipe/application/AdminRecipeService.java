package com.xianzhi.fridge.recipe.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.web.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRecipeService {
    private final RecipeStore store;private final ObjectMapper mapper;private final IdempotencyService idempotency;
    public AdminRecipeService(RecipeStore store,ObjectMapper mapper,IdempotencyService idempotency){this.store=store;this.mapper=mapper;this.idempotency=idempotency;}
    public List<RecipeContracts.SourceView> sources(){return store.sources();}
    @Transactional public RecipeContracts.SourceView create(UUID userId,String key,RecipeContracts.SourceRequest r){String path="/api/v1/admin/recipe-sources";var replay=idempotency.replay(userId,key,"POST",path,r,RecipeContracts.SourceView.class);if(replay!=null)return replay;var result=store.createSource(UuidV7.next(),r);idempotency.save(userId,key,"POST",path,r,result,200);return result;}
    @Transactional public RecipeContracts.SourceView update(UUID userId,UUID id,String key,RecipeContracts.SourceRequest r){String path="/api/v1/admin/recipe-sources/"+id;var replay=idempotency.replay(userId,key,"PATCH",path,r,RecipeContracts.SourceView.class);if(replay!=null)return replay;var result=store.updateSource(id,r);if(result==null)throw new ApiException(HttpStatus.NOT_FOUND,"RECIPE_SOURCE_NOT_FOUND","Recipe source not found");idempotency.save(userId,key,"PATCH",path,r,result,200);return result;}
    @Transactional public RecipeContracts.ImportJobView importJob(UUID userId,String key,RecipeContracts.ImportRequest request){String path="/api/v1/admin/recipe-import-jobs";var replay=idempotency.replay(userId,key,"POST",path,request,RecipeContracts.ImportJobView.class);if(replay!=null)return replay;try{String payload=mapper.writeValueAsString(request.payload());String checksum=Hashing.sha256(request.sourceId()+"\n"+payload);var result=store.findImport(request.sourceId(),checksum);if(result==null)result=store.importJob(UuidV7.next(),request.sourceId(),userId,payload,checksum);idempotency.save(userId,key,"POST",path,request,result,202);return result;}catch(JsonProcessingException e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IMPORT_PAYLOAD","Recipe import payload is invalid");}}
    public RecipeContracts.ImportJobView getImport(UUID id){var value=store.getImport(id);if(value==null)throw new ApiException(HttpStatus.NOT_FOUND,"RECIPE_IMPORT_NOT_FOUND","Recipe import job not found");return value;}
}
