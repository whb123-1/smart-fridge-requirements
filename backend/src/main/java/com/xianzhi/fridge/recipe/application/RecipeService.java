package com.xianzhi.fridge.recipe.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.identity.application.UserPreferenceService;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.inventory.api.InventoryContracts;
import com.xianzhi.fridge.inventory.application.FoodNormalizationService;
import com.xianzhi.fridge.inventory.application.InventoryService;
import com.xianzhi.fridge.inventory.domain.TransactionType;
import com.xianzhi.fridge.nutrition.api.MealContracts;
import com.xianzhi.fridge.nutrition.application.MealService;
import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.web.ApiException;
import java.math.*;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeService {
    private final RecipeStore store;private final UserPreferenceService preferences;private final InventoryService inventory;private final MealService meals;private final IdempotencyService idempotency;private final ObjectMapper mapper;private final Clock clock;private final RecipeVectorIndex vectorIndex;private final FridgeRepository fridges;private final RecipePreferencePolicy preferencePolicy;private final FoodNormalizationService normalization;private RecipeGenerationPort generation;private WebRecipeSearchPort webSearch;
    public RecipeService(RecipeStore store,UserPreferenceService preferences,InventoryService inventory,MealService meals,IdempotencyService idempotency,ObjectMapper mapper,Clock clock,RecipeVectorIndex vectorIndex,FridgeRepository fridges,RecipePreferencePolicy preferencePolicy,FoodNormalizationService normalization){this.store=store;this.preferences=preferences;this.inventory=inventory;this.meals=meals;this.idempotency=idempotency;this.mapper=mapper;this.clock=clock;this.vectorIndex=vectorIndex;this.fridges=fridges;this.preferencePolicy=preferencePolicy;this.normalization=normalization;}
    @Autowired public void setGeneration(RecipeGenerationPort generation){this.generation=generation;}
    @Autowired(required=false) public void setWebSearch(WebRecipeSearchPort webSearch){this.webSearch=webSearch;}
    public List<RecipeContracts.RecipeView> list(UUID userId,Integer maxCook,String taste,String cuisine,BigDecimal maxCalories,String goal,String availability,String query){var preference=preferences.get(userId);List<RecipeStore.Row> candidates=blank(query)?store.all():searchCandidates(query);return candidates.stream().filter(r->safe(r,preference)).filter(r->maxCook==null||r.cookMinutes()<=maxCook).filter(r->blank(taste)||taste.equalsIgnoreCase(r.taste())).filter(r->blank(cuisine)||cuisine.equalsIgnoreCase(r.cuisine())).filter(r->blank(goal)||goal.equalsIgnoreCase(r.goal())).filter(r->maxCalories==null||r.calories()==null||r.calories().compareTo(maxCalories)<=0).map(r->viewV2(userId,r,List.of())).filter(v->blank(availability)||availability.equalsIgnoreCase(v.availability())).toList();}
    public RecipeContracts.RecipeView get(UUID userId,UUID id){return viewV2(userId,accessible(userId,id),List.of());}
    public List<RecipeContracts.RecipeView> generate(UUID userId,RecipeContracts.GenerateRequest request){return generateResult(userId,request).recipes();}
    @Transactional public GenerationResult generateResult(UUID userId,RecipeContracts.GenerateRequest request){List<RecipeContracts.IngredientInput> supplied=validated(userId,request.inventory());String prompt=request.prompt()==null?"":request.prompt().trim();int count=request.count()==null?3:Math.min(3,Math.max(1,request.count()));var preference=preferences.get(userId);boolean promptFirst=promptFirst(request,prompt);if(generation==null)return new GenerationResult(List.of(),"AI 菜谱服务未启用，未生成虚构结果","unavailable",true);RecipeGenerationPort.Discovery discovery=generation.discover(prompt,store.approvedTitles(),supplied,promptFirst?List.of():preference.tastes(),promptFirst?List.of():preference.cuisines(),new ArrayList<>(exclusions(preference)),promptFirst?null:preference.dietaryGoal(),promptFirst?null:preference.calorieTarget(),count);List<RecipeContracts.RecipeView> created=createDrafts(userId,discovery.recipes(),discovery.model(),supplied,preference,promptFirst);return new GenerationResult(created,created.isEmpty()&&!discovery.recipes().isEmpty()?"生成结果触发过敏原或明确忌口，已全部拦截":discovery.rationale(),discovery.model(),discovery.fallback());}
    public GenerationResult recommendResult(UUID userId,RecipeContracts.GenerateRequest request){
        List<RecipeContracts.IngredientInput> supplied=validated(userId,request.inventory());
        String prompt=request.prompt()==null?"":request.prompt().trim();
        boolean selectedMode="SELECTED".equalsIgnoreCase(request.matchMode());
        List<String> preferred=request.preferredIngredients()==null?List.of():request.preferredIngredients().stream().map(String::trim).filter(v->!v.isBlank()).distinct().toList();
        int defaultCount=selectedMode?3:6;
        int count=request.count()==null?defaultCount:Math.min(6,Math.max(1,request.count()));
        boolean inventoryRecommendation=!supplied.isEmpty();
        if(!inventoryRecommendation&&prompt.isBlank())return new GenerationResult(List.of(),"当前没有可用于匹配的库存食材","library-search-v1",false);
        var preference=preferences.get(userId);
        List<ScoredRecipe> eligible=store.all().stream()
                .filter(r->allowed(r,preference))
                .filter(r->inventoryRecommendation||RecipePromptPolicy.matchesSearch(prompt,r,store.components(r.id())))
                .map(r->score(r,prompt,supplied,preferred,preference))
                .filter(v->!inventoryRecommendation||!v.matchedInventory().isEmpty())
                .filter(v->!inventoryRecommendation||v.missing()<=2)
                .filter(v->!selectedMode||!v.matchedPreferred().isEmpty())
                .filter(v->prompt.isBlank()||v.score()>0)
                .sorted(Comparator.comparingInt(ScoredRecipe::score).reversed().thenComparingInt(ScoredRecipe::missing).thenComparing(v->v.row().title()))
                .toList();
        List<ScoredRecipe> chosen=selectedMode?eligible.stream().limit(Math.min(3,count)).toList():diverseRecommendations(eligible,count);
        List<RecipeContracts.RecipeView> recipes=chosen.stream().map(v->viewV2(userId,v.row(),supplied)).toList();
        Set<String> covered=chosen.stream().flatMap(v->v.matchedInventory().stream()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String rationale=recipes.isEmpty()
                ?(!inventoryRecommendation?"现有菜谱库中没有与关键词相关且符合饮食偏好的结果":selectedMode?"现有菜谱库中没有真正使用所选食材且缺料不超过 2 项的结果":"现有菜谱库中没有命中当前库存且符合饮食偏好的结果")
                :(!inventoryRecommendation?"已严格按关键词从现有菜谱库筛选":selectedMode?"每道结果都使用至少一项指定食材，并优先选择缺料更少的方案":"已优先扩大食材覆盖，共覆盖 "+covered.size()+" 项库存食材，避免推荐集中在少数食材");
        return new GenerationResult(recipes,rationale,"library-search-v1",false);
    }
    @Transactional public RecipeContracts.WebSearchResponse searchWeb(UUID userId,RecipeContracts.GenerateRequest request){
        String prompt=request.prompt()==null?"":request.prompt().trim();
        if(prompt.isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请输入菜名、核心食材或做法");
        String sourceMode=blank(request.sourceMode())?"WEB":request.sourceMode().toUpperCase(Locale.ROOT);
        boolean promptFirst=promptFirst(request,prompt);
        String preferenceMode=promptFirst?"PROMPT_FIRST":"PREFERENCE_FIRST";
        int count=request.count()==null?3:Math.min(3,Math.max(1,request.count()));
        List<RecipeContracts.IngredientInput> supplied=List.of();
        var preference=preferences.get(userId);
        List<RecipeContracts.RecipeView> local=new ArrayList<>();
        if(!"WEB".equals(sourceMode)){
            local=searchCandidates(prompt).stream()
                    .filter(row->promptFirst?preferencePolicy.allowsSafety(store.components(row.id()),preference):allowed(row,preference))
                    .limit(count).map(row->viewV2(userId,row,supplied,promptFirst?preferencePolicy.softWarnings(row,preference):List.of())).toList();
        }
        List<String> warnings=new ArrayList<>();
        List<WebRecipeSearchPort.Source> foundSources=List.of();
        List<RecipeContracts.RecipeView> webDrafts=List.of();
        boolean shouldSearch=!"LOCAL".equals(sourceMode);
        boolean fallback=false;
        if(shouldSearch){
            WebRecipeSearchPort.SearchResult search=webSearch==null?new WebRecipeSearchPort.SearchResult(List.of(),List.of("联网搜索未启用"),true):webSearch.search(prompt);
            foundSources=search.sources();warnings.addAll(search.warnings());fallback=search.fallback();
            if(!foundSources.isEmpty()&&generation!=null){
                List<RecipeGenerationPort.WebMaterial> materials=foundSources.stream().map(source->new RecipeGenerationPort.WebMaterial(source.title(),source.summary(),source.url(),source.site())).toList();
                RecipeGenerationPort.Discovery discovery=generation.discoverWithMaterials(prompt,store.approvedTitles(),supplied,
                        promptFirst?List.of():preference.tastes(),promptFirst?List.of():preference.cuisines(),new ArrayList<>(exclusions(preference)),
                        promptFirst?null:preference.dietaryGoal(),promptFirst?null:preference.calorieTarget(),count,materials);
                webDrafts=createDrafts(userId,discovery.recipes(),discovery.model(),supplied,preference,promptFirst);
                if(!webDrafts.isEmpty())store.saveWebSources(webDrafts.stream().map(RecipeContracts.RecipeView::id).toList(),foundSources);
                fallback=fallback||discovery.fallback();
                if(webDrafts.isEmpty()&&!foundSources.isEmpty())warnings.add("来源信息不足或整理结果未通过菜名、主料及安全校验，仅展示搜索来源");
            }else if(generation==null&&!foundSources.isEmpty())warnings.add("AI 生成未启用，仅展示联网搜索来源");
        }
        LinkedHashMap<UUID,RecipeContracts.RecipeView> combined=new LinkedHashMap<>();
        if("WEB".equals(sourceMode))webDrafts.forEach(recipe->combined.put(recipe.id(),recipe));
        else{webDrafts.forEach(recipe->combined.put(recipe.id(),recipe));local.forEach(recipe->combined.putIfAbsent(recipe.id(),recipe));}
        List<RecipeContracts.RecipeView> recipes=combined.values().stream().limit(count).toList();
        List<RecipeContracts.WebSource> sources=foundSources.stream().map(source->new RecipeContracts.WebSource(source.title(),source.summary(),source.url(),source.site(),source.retrievedAt(),source.sourceVersion())).toList();
        String model=webDrafts.isEmpty()?"library-search-v1":webDrafts.getFirst().sourceVersion();
        String rationale=recipes.isEmpty()?"没有找到通过来源、提示词和安全校验的可入库菜谱"
                :"WEB".equals(sourceMode)?"已仅按提示词和公开网页来源整理新菜谱；确认后可加入菜谱库"
                :"LOCAL".equals(sourceMode)?"已按提示词查询现有菜谱库"
                :"已按提示词匹配本地菜谱，并用公开网页来源补充；外部菜谱仍需确认后入库";
        return new RecipeContracts.WebSearchResponse(recipes,sources,warnings.stream().distinct().toList(),model,fallback,rationale,sourceMode,preferenceMode,
                webDrafts.stream().map(RecipeContracts.RecipeView::id).toList());
    }
    @Transactional public List<RecipeContracts.RecipeView> publishGenerated(UUID userId,String key,RecipeContracts.GeneratedRecipeSelection request){
        String path="/api/v1/recipes/generated/publish";
        PublishReplay replay=idempotency.replay(userId,key,"POST",path,request,PublishReplay.class);
        if(replay!=null)return replay.recipes();
        var preference=preferences.get(userId);List<RecipeContracts.RecipeView> published=new ArrayList<>();
        for(UUID id:new LinkedHashSet<>(request.recipeIds())){RecipeStore.Row draft=store.findOwnedDraft(userId,id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RECIPE_DRAFT_NOT_FOUND","AI 菜谱草稿不存在或已处理"));if(!preferencePolicy.allowsSafety(store.components(draft.id()),preference))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"RECIPE_SAFETY_CONFLICT","该菜谱包含过敏原或明确忌口，不能加入菜谱库");if(store.titleExists(draft.title()))throw new ApiException(HttpStatus.CONFLICT,"RECIPE_ALREADY_EXISTS","现有菜谱库已包含同名菜谱");if(store.publishOwnedDraft(userId,id))published.add(viewV2(userId,store.find(id).orElseThrow(),List.of()));}
        idempotency.save(userId,key,"POST",path,request,new PublishReplay(published),200);
        return published;
    }
    @Transactional public int discardGenerated(UUID userId,List<UUID> recipeIds){return store.discardOwnedDrafts(userId,new ArrayList<>(new LinkedHashSet<>(recipeIds)));}
    @Transactional public RecipeContracts.MatchView match(UUID userId,RecipeContracts.MatchRequest request){
        List<RecipeContracts.IngredientInput> inputs=validated(userId,request.ingredients());
        if(inputs.isEmpty())throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SYNTHESIS_INVENTORY_INVALID","所选食材已不在可用库存中，请刷新后重试");
        var preference=preferences.get(userId);
        List<SynthesisCandidate> candidates=store.all().stream()
                .filter(row->allowed(row,preference))
                .map(row->synthesisCandidate(userId,row,inputs))
                .filter(candidate->!candidate.matched().isEmpty())
                .filter(candidate->candidate.recipe().missing().size()<=2)
                .sorted(Comparator.comparingInt((SynthesisCandidate candidate)->candidate.unmatched().size())
                        .thenComparingInt(candidate->candidate.recipe().missing().size())
                        .thenComparingInt(candidate->candidate.recipe().cookMinutes())
                        .thenComparing(candidate->candidate.recipe().name()))
                .limit(5)
                .toList();
        boolean aiFallback=false;
        if(candidates.isEmpty()&&generation!=null){
            String prompt="使用这些食材设计可执行菜谱："+inputs.stream().map(RecipeContracts.IngredientInput::name).distinct().reduce((a,b)->a+"、"+b).orElse("");
            RecipeGenerationPort.Discovery discovery=generation.discover(prompt,store.approvedTitles(),inputs,preference.tastes(),preference.cuisines(),new ArrayList<>(exclusions(preference)),preference.dietaryGoal(),preference.calorieTarget(),3);
            candidates=createDrafts(userId,discovery.recipes(),discovery.model(),inputs,preference).stream().map(recipe->synthesisCandidate(recipe,inputs)).filter(candidate->!candidate.matched().isEmpty()).limit(3).toList();
            aiFallback=!candidates.isEmpty();
        }
        List<RecipeContracts.RecipeView> recipes=candidates.stream().map(SynthesisCandidate::recipe).toList();
        List<String> matched=candidates.isEmpty()?List.of():candidates.getFirst().matched();
        List<String> unmatched=candidates.isEmpty()?inputs.stream().map(RecipeContracts.IngredientInput::name).distinct().toList():candidates.getFirst().unmatched();
        List<String> suggestions=candidates.stream().flatMap(candidate->candidate.recipe().missing().stream()).distinct().limit(5).toList();
        UUID synthesisId=UuidV7.next();
        try{store.synthesis(synthesisId,userId,recipes.isEmpty()?null:recipes.getFirst().id(),mapper.writeValueAsString(request),recipes.isEmpty()?"UNMATCHED":"MATCHED");}
        catch(JsonProcessingException e){throw new IllegalStateException(e);}
        return new RecipeContracts.MatchView(synthesisId,recipes,matched,unmatched,suggestions,aiFallback?"AI_DRAFT":"LIBRARY");
    }
    public RecipeContracts.ScaleView scale(UUID userId,UUID id,RecipeContracts.ScaleRequest request){RecipeStore.Row row=accessible(userId,id);List<RecipeContracts.Component> base=store.components(id);RecipeContracts.Component primary=base.stream().filter(v->v.id().equals(request.primaryComponentId())).findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RECIPE_COMPONENT_NOT_FOUND","Recipe component not found"));if(!primary.unit().equals(request.unit()))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"UNIT_NOT_CONVERTIBLE","Primary ingredient unit is not convertible");BigDecimal ratio=request.quantity().divide(primary.quantity(),8,RoundingMode.HALF_UP);List<RecipeContracts.Component> scaled=base.stream().map(c->scale(c,ratio)).toList();BigDecimal nutritionScale=ratio;RecipeContracts.Nutrition total=nutrition(row,nutritionScale);return new RecipeContracts.ScaleView(id,request.servings(),scaled,total,divide(total,request.servings()));}
    @Transactional public void bookmark(UUID userId,UUID id){accessible(userId,id);store.bookmark(userId,id,UuidV7.next());store.event(UuidV7.next(),userId,id,"BOOKMARKED","{}");}
    @Transactional public void unbookmark(UUID userId,UUID id){accessible(userId,id);store.unbookmark(userId,id);store.event(UuidV7.next(),userId,id,"UNBOOKMARKED","{}");}
    public List<RecipeContracts.PlannedRecipeView> plans(UUID userId,UUID fridgeId){requireFridge(userId,fridgeId);return store.plans(userId,fridgeId).stream().filter(row->visible(userId,row.recipeId()).isPresent()).map(row->planView(userId,row)).toList();}
    @Transactional public RecipeContracts.PlannedRecipeView addPlan(UUID userId,UUID fridgeId,RecipeContracts.PlanCreateRequest request){requireFridge(userId,fridgeId);accessible(userId,request.recipeId());RecipeStore.PlanRow row=store.savePlan(UuidV7.next(),userId,fridgeId,request.recipeId(),request.servings());store.event(UuidV7.next(),userId,request.recipeId(),"PLANNED","{}");return planView(userId,row);}
    @Transactional public RecipeContracts.PlannedRecipeView updatePlan(UUID userId,UUID id,RecipeContracts.PlanUpdateRequest request){RecipeStore.PlanRow current=store.plan(userId,id);if(current==null)throw new ApiException(HttpStatus.NOT_FOUND,"RECIPE_PLAN_NOT_FOUND","Planned recipe not found");accessible(userId,current.recipeId());RecipeStore.PlanRow row=store.updatePlan(userId,id,request.servings());return planView(userId,row);}
    @Transactional public void deletePlan(UUID userId,UUID id){RecipeStore.PlanRow row=store.plan(userId,id);if(row==null||!store.deletePlan(userId,id))throw new ApiException(HttpStatus.NOT_FOUND,"RECIPE_PLAN_NOT_FOUND","Planned recipe not found");store.event(UuidV7.next(),userId,row.recipeId(),"UNPLANNED","{}");}
    @Transactional public RecipeContracts.CookView cook(UUID userId,UUID id,String key,RecipeContracts.CookRequest request){String path="/api/v1/recipes/"+id+"/cook";RecipeContracts.CookView replay=idempotency.replay(userId,key,"POST",path,request,RecipeContracts.CookView.class);if(replay!=null)return replay;RecipeStore.Row row=accessible(userId,id);if(request.synthesisId()!=null&&!store.executeSynthesis(userId,request.synthesisId(),id))throw new ApiException(HttpStatus.CONFLICT,"SYNTHESIS_CONTEXT_INVALID","Synthesis session is missing, stale, or belongs to another recipe");List<UUID> batchIds=new ArrayList<>();int index=0;for(var use:request.consumptions()){String nested="cook-"+Hashing.sha256(userId+":"+key+":"+index++).substring(0,64);inventory.transact(userId,use.batchId(),nested,new InventoryContracts.TransactionRequest(TransactionType.CONSUME,use.quantity(),use.unit(),"RECIPE_COOK"));batchIds.add(use.batchId());}BigDecimal factor=request.servings().divide(row.servings(),8,RoundingMode.HALF_UP);RecipeContracts.Nutrition n=nutrition(row,factor);UUID mealId=request.recordMeal()?meals.recordRecipe(userId,id,request.mealAt()==null?clock.instant():request.mealAt(),request.servings(),row.title(),new MealContracts.NutritionView(n.calories(),n.protein(),n.fat(),n.carbs(),false,row.nutritionSource(),MealService.DISCLAIMER)):null;try{store.event(UuidV7.next(),userId,id,"COOKED",mapper.writeValueAsString(Map.of("batchIds",batchIds,"synthesisId",request.synthesisId()==null?"":request.synthesisId().toString())));}catch(JsonProcessingException e){throw new IllegalStateException(e);}RecipeContracts.CookView result=new RecipeContracts.CookView(id,batchIds,mealId);idempotency.save(userId,key,"POST",path,request,result,200);return result;}
    private List<RecipeContracts.RecipeView> createDrafts(UUID userId,List<RecipeGenerationPort.Draft> drafts,String model,List<RecipeContracts.IngredientInput> supplied,com.xianzhi.fridge.identity.api.PreferenceContracts.View preference){return createDrafts(userId,drafts,model,supplied,preference,false);}
    private List<RecipeContracts.RecipeView> createDrafts(UUID userId,List<RecipeGenerationPort.Draft> drafts,String model,List<RecipeContracts.IngredientInput> supplied,com.xianzhi.fridge.identity.api.PreferenceContracts.View preference,boolean promptFirst){List<RecipeContracts.RecipeView> created=new ArrayList<>();for(RecipeGenerationPort.Draft draft:drafts){if(store.titleExists(draft.title()))continue;String signature=userId+"|"+draft.title()+"|"+draft.ingredients()+"|"+UuidV7.next();UUID id=store.insertAiDraft(userId,draft,Hashing.sha256(signature),model);RecipeStore.Row row=store.findOwnedDraft(userId,id).orElseThrow();boolean safe=preferencePolicy.allowsSafety(store.components(row.id()),preference);if(!safe||(!promptFirst&&!allowed(row,preference))){store.discardOwnedDrafts(userId,List.of(id));continue;}created.add(viewV2(userId,row,supplied,promptFirst?preferencePolicy.softWarnings(row,preference):List.of()));}return created;}
    private RecipeContracts.RecipeView viewV2(UUID userId,RecipeStore.Row row,List<RecipeContracts.IngredientInput> supplied){return viewV2(userId,row,supplied,List.of());}
    private RecipeContracts.RecipeView viewV2(UUID userId,RecipeStore.Row row,List<RecipeContracts.IngredientInput> supplied,List<String> extraWarnings){List<RecipeContracts.Component> components=store.components(row.id());List<String> warnings=new ArrayList<>(extraWarnings);List<String> missing=supplied.isEmpty()?List.of():components.stream().filter(component->!"SEASONING".equals(component.role())).filter(component->!satisfies(component,supplied,warnings)).map(RecipeContracts.Component::name).toList();String availability=supplied.isEmpty()?"UNKNOWN":missing.isEmpty()?"DIRECT":missing.size()<=2?"MISSING_FEW":"UNMATCHED";RecipeContracts.Nutrition total=nutrition(row,BigDecimal.ONE);List<String> steps=store.steps(row.id());return new RecipeContracts.RecipeView(row.id(),row.title(),row.summary(),row.cuisine(),row.taste(),row.goal(),row.cookMinutes(),row.servings(),total,divide(total,row.servings()),components,steps,detailedSteps(row,steps),utensils(row,steps),row.nutritionSource(),store.bookmarked(userId,row.id()),row.origin(),row.sourceVersion(),row.attribution(),row.imageUrl(),row.imageSourceUrl(),row.imageAttribution(),availability,missing,warnings,store.webSources(row.id()));}
    private SynthesisCandidate synthesisCandidate(UUID userId,RecipeStore.Row row,List<RecipeContracts.IngredientInput> inputs){RecipeContracts.RecipeView recipe=viewV2(userId,row,inputs);List<String> matched=inputs.stream().filter(input->recipe.ingredients().stream().filter(component->!"SEASONING".equals(component.role())).anyMatch(component->similar(input.name(),component.name()))).map(RecipeContracts.IngredientInput::name).distinct().toList();List<String> unmatched=inputs.stream().filter(input->matched.stream().noneMatch(name->similar(input.name(),name))).map(RecipeContracts.IngredientInput::name).distinct().toList();return new SynthesisCandidate(recipe,matched,unmatched);}
    private SynthesisCandidate synthesisCandidate(RecipeContracts.RecipeView recipe,List<RecipeContracts.IngredientInput> inputs){List<String> matched=inputs.stream().filter(input->recipe.ingredients().stream().filter(component->!"SEASONING".equals(component.role())).anyMatch(component->similar(input.name(),component.name()))).map(RecipeContracts.IngredientInput::name).distinct().toList();List<String> unmatched=inputs.stream().filter(input->matched.stream().noneMatch(name->similar(input.name(),name))).map(RecipeContracts.IngredientInput::name).distinct().toList();return new SynthesisCandidate(recipe,matched,unmatched);}
    private List<RecipeContracts.IngredientInput> validated(UUID userId,List<RecipeContracts.IngredientInput> values){if(values==null)return List.of();return values.stream().filter(value->value.batchId()==null||inventory.isUsableForRecipe(userId,value.batchId(),value.quantity(),value.unit())).toList();}
    private boolean satisfies(RecipeContracts.Component component,List<RecipeContracts.IngredientInput> supplied,List<String> warnings){for(var input:supplied){if(!similar(input.name(),component.name()))continue;if(input.unit()==null||!input.unit().equals(component.unit())){warnings.add("UNIT_NOT_COMPARABLE:"+component.name());return true;}if(input.quantity()==null||input.quantity().compareTo(component.quantity())>=0)return true;}return false;}
    private boolean allowed(RecipeStore.Row row,com.xianzhi.fridge.identity.api.PreferenceContracts.View preference){return preferencePolicy.allows(row,store.components(row.id()),preference);}
    private boolean safe(RecipeStore.Row row,com.xianzhi.fridge.identity.api.PreferenceContracts.View preference){return preferencePolicy.allowsSafety(store.components(row.id()),preference);}
    private static Set<String> exclusions(com.xianzhi.fridge.identity.api.PreferenceContracts.View p){Set<String> values=new LinkedHashSet<>(p.allergies());values.addAll(p.dislikes());return values;}
    private static boolean promptFirst(RecipeContracts.GenerateRequest request,String prompt){return "PROMPT_FIRST".equalsIgnoreCase(request.preferenceMode())||(blank(request.preferenceMode())&&!prompt.isBlank());}
    private List<RecipeStore.Row> searchCandidates(String query){LinkedHashMap<UUID,RecipeStore.Row> combined=new LinkedHashMap<>();for(RecipeStore.Row row:store.search(query))combined.put(row.id(),row);List<UUID> vectorIds=vectorIndex.search(query,20);if(!vectorIds.isEmpty())for(RecipeStore.Row row:store.byIds(vectorIds))combined.putIfAbsent(row.id(),row);return combined.values().stream().filter(row->RecipePromptPolicy.matchesSearch(query,row,store.components(row.id()))).toList();}
    private List<ScoredRecipe> diverseRecommendations(List<ScoredRecipe> candidates,int count){List<ScoredRecipe> remaining=new ArrayList<>(candidates);List<ScoredRecipe> selected=new ArrayList<>();Set<String> covered=new LinkedHashSet<>();while(!remaining.isEmpty()&&selected.size()<count){ScoredRecipe next=remaining.stream().max(Comparator.comparingInt((ScoredRecipe value)->marginalScore(value,covered)).thenComparing(value->value.row().title(),Comparator.reverseOrder())).orElseThrow();selected.add(next);covered.addAll(next.matchedInventory());remaining.remove(next);}return selected;}
    private static int marginalScore(ScoredRecipe value,Set<String> covered){long newMatches=value.matchedInventory().stream().filter(name->!covered.contains(name)).count();return value.score()+(int)newMatches*1000;}
    private ScoredRecipe score(RecipeStore.Row row,String prompt,List<RecipeContracts.IngredientInput> supplied,List<String> preferred,com.xianzhi.fridge.identity.api.PreferenceContracts.View preference){List<RecipeContracts.Component> components=store.components(row.id());List<RecipeContracts.Component> foodComponents=components.stream().filter(component->!"SEASONING".equals(component.role())).toList();Set<String> matchedInventory=supplied.stream().filter(input->foodComponents.stream().anyMatch(component->similar(input.name(),component.name()))).map(input->input.name().trim()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));Set<String> matchedPreferred=preferred.stream().filter(name->foodComponents.stream().anyMatch(component->similar(name,component.name()))).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));int matched=(int)foodComponents.stream().filter(component->supplied.stream().anyMatch(input->similar(input.name(),component.name()))).count();int missing=(int)foodComponents.stream().filter(component->!satisfies(component,supplied,new ArrayList<>())).count();int score=matched*18+matchedPreferred.size()*30-missing*9;if(prompt.isBlank())score+=8;else{String normalized=prompt.toLowerCase(Locale.ROOT);String title=row.title().toLowerCase(Locale.ROOT);String summary=row.summary()==null?"":row.summary().toLowerCase(Locale.ROOT);if(title.contains(normalized))score+=80;if(summary.contains(normalized))score+=20;for(var component:components){String ingredient=component.name().toLowerCase(Locale.ROOT);if(normalized.contains(ingredient)||ingredient.contains(normalized))score+=45;}for(String attribute:Arrays.asList(row.taste(),row.cuisine(),row.goal()))if(attribute!=null&&normalized.contains(attribute.toLowerCase(Locale.ROOT)))score+=20;for(String token:tokens(normalized))if(title.contains(token)||summary.contains(token)||components.stream().anyMatch(c->c.name().toLowerCase(Locale.ROOT).contains(token)))score+=10;}if(preference.tastes().stream().anyMatch(v->v.equalsIgnoreCase(row.taste())))score+=8;if(preference.cuisines().stream().anyMatch(v->v.equalsIgnoreCase(row.cuisine())))score+=8;if(preference.dietaryGoal()!=null&&preference.dietaryGoal().contains("减脂")&&row.calories()!=null&&row.calories().compareTo(BigDecimal.valueOf(500))<=0)score+=8;if(preference.calorieTarget()!=null&&row.calories()!=null&&row.calories().compareTo(BigDecimal.valueOf(preference.calorieTarget()))<=0)score+=4;return new ScoredRecipe(row,score,missing,matchedInventory,matchedPreferred);}
    private RecipeGenerationPort.Candidate candidate(RecipeStore.Row row,List<RecipeContracts.IngredientInput> supplied,int missing){List<RecipeContracts.Component> components=store.components(row.id());int matched=(int)components.stream().filter(c->!"SEASONING".equals(c.role())).filter(c->supplied.stream().anyMatch(i->similar(i.name(),c.name()))).count();return new RecipeGenerationPort.Candidate(row.id(),row.title(),row.summary(),row.cuisine(),row.taste(),row.goal(),row.cookMinutes(),components.stream().map(RecipeContracts.Component::name).toList(),matched,missing);}
    private static List<String> tokens(String value){return Arrays.stream(value.split("[\\s,，、。；;]+" )).map(String::trim).filter(v->v.length()>=2).toList();}
    private Optional<RecipeStore.Row> visible(UUID userId,UUID id){return store.find(id).filter(row->safe(row,preferences.get(userId)));}
    private RecipeStore.Row accessible(UUID userId,UUID id){return visible(userId,id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RECIPE_NOT_FOUND","菜谱不存在或包含当前账户设置的过敏原、明确忌口"));}
    private void requireFridge(UUID userId,UUID fridgeId){if(fridgeId==null||fridges.findById(fridgeId).filter(fridge->userId.equals(fridge.getUserId())&&fridge.getDeletedAt()==null).isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"FRIDGE_NOT_FOUND","Fridge not found");}
    private RecipeContracts.PlannedRecipeView planView(UUID userId,RecipeStore.PlanRow row){RecipeStore.Row recipe=accessible(userId,row.recipeId());return new RecipeContracts.PlannedRecipeView(row.id(),row.fridgeId(),row.recipeId(),row.servings(),row.createdAt(),viewV2(userId,recipe,List.of()));}
    private static List<RecipeContracts.DetailedStep> detailedSteps(RecipeStore.Row row,List<String> steps){int minutes=Math.max(1,(int)Math.ceil(row.cookMinutes()/(double)Math.max(1,steps.size())));List<RecipeContracts.DetailedStep> output=new ArrayList<>();for(int index=0;index<steps.size();index++){String instruction=steps.get(index);String title=instruction.matches(".*(洗|切|擦干|打散|腌).*" )?"备料":instruction.matches(".*(焯|煮开|预热).*" )?"预处理":instruction.matches(".*(炒|煎|蒸|烤|煮).*" )?"烹制":"调味装盘";String heat=instruction.matches(".*(大火|水开|沸).*" )?"大火":instruction.matches(".*(小火|慢炖|焖).*" )?"小火":instruction.matches(".*(中火|煎|炒).*" )?"中火":"按状态调整";String checkpoint=instruction.matches(".*(鸡|肉|鱼|虾).*" )?"中心熟透、没有生色再进入下一步":instruction.contains("蛋")?"蛋液凝固但仍保持嫩滑":instruction.matches(".*(蔬菜|青椒|西兰花|生菜).*" )?"颜色明亮、刚断生即可":"观察香气、颜色和质地，避免只依赖计时";output.add(new RecipeContracts.DetailedStep(index+1,title,instruction,"约 "+minutes+" 分钟",heat,checkpoint));}return output;}
    private static List<String> utensils(RecipeStore.Row row,List<String> steps){String text=row.title()+" "+String.join(" ",steps);LinkedHashSet<String> tools=new LinkedHashSet<>(List.of("菜刀","砧板"));if(text.matches(".*(蒸|清蒸).*"))tools.add("蒸锅");if(text.matches(".*(炒|翻炒|爆香).*"))tools.add("炒锅");if(text.matches(".*(煎|煎至).*"))tools.add("平底锅");if(text.matches(".*(烤|烘烤).*"))tools.add("烤箱");if(text.matches(".*(煮|焯|汤|粥|面).*"))tools.add("汤锅");if(text.matches(".*(打散|腌制|拌匀).*"))tools.add("料理碗");if(tools.size()==2)tools.add("烹饪锅具");return new ArrayList<>(tools);}
    private RecipeContracts.Component scale(RecipeContracts.Component c,BigDecimal ratio){BigDecimal q;if("FIXED".equals(c.scalingRule()))q=c.quantity();else if("BOUNDED".equals(c.scalingRule())){q=c.quantity().multiply(BigDecimal.valueOf(Math.pow(ratio.doubleValue(),0.75)));if(c.minimumQuantity()!=null)q=q.max(c.minimumQuantity());if(c.maximumQuantity()!=null)q=q.min(c.maximumQuantity());}else q=c.quantity().multiply(ratio);return new RecipeContracts.Component(c.id(),c.name(),c.role(),q.setScale(3,RoundingMode.HALF_UP),c.unit(),c.scalingRule(),c.minimumQuantity(),c.maximumQuantity());}
    private static RecipeContracts.Nutrition nutrition(RecipeStore.Row r,BigDecimal factor){return new RecipeContracts.Nutrition(mul(r.calories(),factor),mul(r.protein(),factor),mul(r.fat(),factor),mul(r.carbs(),factor));}
    private static RecipeContracts.Nutrition divide(RecipeContracts.Nutrition n,BigDecimal servings){return new RecipeContracts.Nutrition(div(n.calories(),servings),div(n.protein(),servings),div(n.fat(),servings),div(n.carbs(),servings));}
    private static BigDecimal mul(BigDecimal v,BigDecimal f){return v==null?null:v.multiply(f).setScale(3,RoundingMode.HALF_UP);}private static BigDecimal div(BigDecimal v,BigDecimal d){return v==null?null:v.divide(d,3,RoundingMode.HALF_UP);}private boolean similar(String a,String b){return a!=null&&b!=null&&(a.contains(b)||b.contains(a)||normalization.equivalent(a,b));}private static boolean blank(String v){return v==null||v.isBlank();}public record GenerationResult(List<RecipeContracts.RecipeView> recipes,String rationale,String model,boolean fallback){}private record ScoredRecipe(RecipeStore.Row row,int score,int missing,Set<String> matchedInventory,Set<String> matchedPreferred) { }private record SynthesisCandidate(RecipeContracts.RecipeView recipe,List<String> matched,List<String> unmatched) { }private record PublishReplay(List<RecipeContracts.RecipeView> recipes) { }
}
