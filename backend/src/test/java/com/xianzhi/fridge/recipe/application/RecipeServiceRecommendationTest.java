package com.xianzhi.fridge.recipe.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.identity.api.PreferenceContracts;
import com.xianzhi.fridge.identity.application.UserPreferenceService;
import com.xianzhi.fridge.inventory.application.InventoryService;
import com.xianzhi.fridge.inventory.application.FoodNormalizationService;
import com.xianzhi.fridge.nutrition.application.MealService;
import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeServiceRecommendationTest {
    private static final PreferenceContracts.View EMPTY_PREFERENCES =
            new PreferenceContracts.View(List.of(), List.of(), List.of(), List.of(), null, null, null);

    private final RecipeStore store = mock(RecipeStore.class);
    private final UserPreferenceService preferences = mock(UserPreferenceService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final RecipeVectorIndex vectorIndex = mock(RecipeVectorIndex.class);
    private final FoodNormalizationService normalization = mock(FoodNormalizationService.class);
    private final Map<UUID, List<RecipeContracts.Component>> components = new java.util.LinkedHashMap<>();
    private RecipeService service;

    @BeforeEach
    void setUp() {
        when(preferences.get(any())).thenReturn(EMPTY_PREFERENCES);
        when(store.components(any())).thenAnswer(invocation -> components.getOrDefault(invocation.getArgument(0), List.of()));
        when(store.steps(any())).thenReturn(List.of("处理食材", "烹饪至熟"));
        when(normalization.equivalent(any(), any())).thenAnswer(invocation -> {
            String first = invocation.getArgument(0);
            String second = invocation.getArgument(1);
            return Set.of("番茄", "西红柿").contains(first) && Set.of("番茄", "西红柿").contains(second);
        });
        service = new RecipeService(store, preferences, inventory, mock(MealService.class), mock(IdempotencyService.class),
                new ObjectMapper(), Clock.systemUTC(), vectorIndex, mock(FridgeRepository.class), new RecipePreferencePolicy(), normalization);
    }

    @Test
    void beefSearchRejectsSemanticResultsWithoutBeef() {
        RecipeStore.Row fish = recipe("清蒸鲈鱼", "少油清蒸", "鲈鱼");
        RecipeStore.Row beef = recipe("洋葱牛肉片", "鲜香下饭", "牛肉片", "洋葱");
        when(store.search("牛肉")).thenReturn(List.of());
        when(vectorIndex.search("牛肉", 20)).thenReturn(List.of(fish.id(), beef.id()));
        when(store.byIds(List.of(fish.id(), beef.id()))).thenReturn(List.of(fish, beef));

        List<RecipeContracts.RecipeView> result = service.list(UUID.randomUUID(), null, null, null, null, null, null, "牛肉");

        assertThat(result).extracting(RecipeContracts.RecipeView::name).containsExactly("洋葱牛肉片");
    }

    @Test
    void selectedTomatoModeNeverReturnsRecipesWithoutTomato() {
        RecipeStore.Row tomato = recipe("番茄鸡蛋面", "一锅完成", "番茄", "鸡蛋", "面条");
        RecipeStore.Row fish = recipe("清蒸鲈鱼", "少油清蒸", "鲈鱼");
        when(store.all()).thenReturn(List.of(fish, tomato));
        List<RecipeContracts.IngredientInput> stock = List.of(
                ingredient("番茄", 2, "piece"), ingredient("鸡蛋", 4, "piece"), ingredient("面条", 500, "g"),
                ingredient("鲈鱼", 600, "g"));

        RecipeService.GenerationResult result = service.recommendResult(UUID.randomUUID(),
                new RecipeContracts.GenerateRequest(null, "", stock, List.of("番茄"), "SELECTED", 3));

        assertThat(result.recipes()).extracting(RecipeContracts.RecipeView::name).containsExactly("番茄鸡蛋面");
        assertThat(result.recipes()).allSatisfy(recipe -> assertThat(recipe.ingredients())
                .extracting(RecipeContracts.Component::name).anyMatch(name -> name.contains("番茄")));
    }

    @Test
    void selectedTomatoAliasMatchesCanonicalRecipeIngredient() {
        RecipeStore.Row tomato = recipe("番茄炒蛋", "家常快手", "番茄", "鸡蛋");
        RecipeStore.Row fish = recipe("清蒸鲈鱼", "少油清蒸", "鲈鱼");
        when(store.all()).thenReturn(List.of(fish, tomato));
        List<RecipeContracts.IngredientInput> stock = List.of(ingredient("西红柿", 500, "g"));

        RecipeService.GenerationResult result = service.recommendResult(UUID.randomUUID(),
                new RecipeContracts.GenerateRequest(null, "", stock, List.of("西红柿"), "SELECTED", 3));

        assertThat(result.recipes()).extracting(RecipeContracts.RecipeView::name).containsExactly("番茄炒蛋");
    }

    @Test
    void allInventoryModeExpandsCoverageAcrossChosenRecipes() {
        RecipeStore.Row tomatoEgg = recipe("番茄炒蛋", "家常快手", "番茄", "鸡蛋");
        RecipeStore.Row tomatoOnion = recipe("番茄洋葱汤", "酸甜汤品", "番茄", "洋葱");
        RecipeStore.Row beefOnion = recipe("洋葱牛肉", "鲜香下饭", "牛肉", "洋葱");
        when(store.all()).thenReturn(List.of(tomatoEgg, tomatoOnion, beefOnion));
        List<RecipeContracts.IngredientInput> stock = List.of(
                ingredient("番茄", 500, "g"), ingredient("鸡蛋", 4, "piece"),
                ingredient("牛肉", 400, "g"), ingredient("洋葱", 300, "g"));

        RecipeService.GenerationResult result = service.recommendResult(UUID.randomUUID(),
                new RecipeContracts.GenerateRequest(null, "", stock, List.of(), "ALL", 2));

        Set<String> covered = new LinkedHashSet<>();
        result.recipes().forEach(recipe -> recipe.ingredients().stream()
                .map(RecipeContracts.Component::name)
                .filter(name -> stock.stream().anyMatch(item -> name.contains(item.name()) || item.name().contains(name)))
                .forEach(covered::add));
        assertThat(result.recipes()).hasSize(2);
        assertThat(covered).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.rationale()).contains("扩大食材覆盖");
    }

    @Test
    void publishedAiRecipeRemainsVisibleDespiteSoftPreferenceMismatchAndIsNotMarkedAsDraft() {
        RecipeStore.Row base = recipe("番茄红烧虾", "鲜虾与番茄同烧", "虾", "番茄");
        RecipeStore.Row publishedAiRecipe = new RecipeStore.Row(base.id(), base.title(), base.summary(), base.cuisine(),
                base.taste(), base.goal(), base.cookMinutes(), base.servings(), base.calories(), base.protein(),
                base.fat(), base.carbs(), base.nutritionSource(), "AI_GENERATED", base.sourceVersion(),
                base.attribution(), base.imageUrl(), base.imageSourceUrl(), base.imageAttribution());
        when(store.search("番茄红烧虾")).thenReturn(List.of(publishedAiRecipe));
        when(vectorIndex.search("番茄红烧虾", 20)).thenReturn(List.of());
        when(preferences.get(any())).thenReturn(new PreferenceContracts.View(List.of("清淡"), List.of("粤菜"),
                List.of(), List.of(), "减脂", 1200, null));

        RecipeContracts.WebSearchResponse result = service.searchWeb(UUID.randomUUID(),
                new RecipeContracts.GenerateRequest(null, "番茄红烧虾", List.of(), List.of(), "ALL", 3,
                        "LOCAL", "PROMPT_FIRST"));

        assertThat(result.recipes()).singleElement().satisfies(recipe -> assertThat(recipe.source()).isEqualTo("AI_GENERATED"));
        assertThat(result.draftRecipeIds()).isEmpty();
        assertThat(service.list(UUID.randomUUID(), null, null, null, null, null, null, "番茄红烧虾"))
                .extracting(RecipeContracts.RecipeView::name).containsExactly("番茄红烧虾");
    }

    @Test
    void webPromptSearchIgnoresInventoryFromOlderClients() {
        RecipeContracts.IngredientInput inventoryItem = new RecipeContracts.IngredientInput(
                UUID.randomUUID(), "番茄", BigDecimal.valueOf(500), "g");

        RecipeContracts.WebSearchResponse result = service.searchWeb(UUID.randomUUID(),
                new RecipeContracts.GenerateRequest(null, "红烧鹅", List.of(inventoryItem), List.of(), "ALL", 3));

        verifyNoInteractions(inventory);
        assertThat(result.sourceMode()).isEqualTo("WEB");
    }

    private RecipeStore.Row recipe(String title, String summary, String... ingredientNames) {
        UUID id = UUID.randomUUID();
        List<RecipeContracts.Component> recipeComponents = new ArrayList<>();
        for (int index = 0; index < ingredientNames.length; index++) {
            String name = ingredientNames[index];
            String unit = name.equals("鸡蛋") ? "piece" : "g";
            recipeComponents.add(new RecipeContracts.Component(UUID.randomUUID(), name, "PRIMARY", BigDecimal.valueOf(100),
                    unit, "LINEAR", null, null));
        }
        components.put(id, recipeComponents);
        return new RecipeStore.Row(id, title, summary, "家常菜", "咸鲜", "均衡", 20, BigDecimal.valueOf(2),
                BigDecimal.valueOf(500), BigDecimal.valueOf(30), BigDecimal.valueOf(20), BigDecimal.valueOf(40),
                "CURATED", "CURATED", "1", "测试菜谱", null, null, null);
    }

    private static RecipeContracts.IngredientInput ingredient(String name, double quantity, String unit) {
        return new RecipeContracts.IngredientInput(null, name, BigDecimal.valueOf(quantity), unit);
    }
}
