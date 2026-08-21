package com.xianzhi.fridge.recipe.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenAiRecipeGenerationAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOnlyCandidateIdsAndPreservesRequestedCount() throws Exception {
        AssistantProperties properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        properties.setModelName("test-model");
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var output = mapper.createObjectNode();
        output.putArray("recipeIds").add(first.toString()).add("not-a-candidate").add(second.toString());
        output.put("rationale", "库存匹配度高");
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message")
                .put("content", mapper.writeValueAsString(output));
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);
        var adapter = new OpenAiRecipeGenerationAdapter(properties, client, mapper);
        var result = adapter.rank("晚餐", List.of(candidate(first), candidate(second)),
                List.of(new RecipeContracts.IngredientInput(null, "鸡蛋", BigDecimal.ONE, "piece")),
                List.of(), List.of(), null, null, 2);
        assertThat(result.fallback()).isFalse();
        assertThat(result.recipeIds()).containsExactly(first, second);
    }

    @Test
    void fallsBackToRulesWhenExternalAiIsDisabled() {
        AssistantProperties properties = new AssistantProperties();
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        UUID id = UUID.randomUUID();
        var result = new OpenAiRecipeGenerationAdapter(properties, client, mapper).rank("", List.of(candidate(id)),
                List.of(), List.of(), List.of(), null, null, 1);
        assertThat(result.fallback()).isTrue();
        assertThat(result.recipeIds()).containsExactly(id);
    }

    @Test
    void fallsBackWhenProviderReturnsInvalidStructuredOutput() {
        AssistantProperties properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        UUID id = UUID.randomUUID();
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", "not-json");
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);

        var result = new OpenAiRecipeGenerationAdapter(properties, client, mapper).rank("晚餐想吃清淡的",
                List.of(candidate(id)), List.of(), List.of("清淡"), List.of(), null, null, 1);

        assertThat(result.fallback()).isTrue();
        assertThat(result.model()).isEqualTo("rules-v2");
        assertThat(result.recipeIds()).containsExactly(id);
    }

    @Test
    void discoversOnlyValidRecipesThatDoNotReuseExistingTitles() throws Exception {
        AssistantProperties properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        properties.setModelName("test-model");
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        var duplicate = mapper.createObjectNode();
        duplicate.put("title", "番茄炒蛋");
        duplicate.put("summary", "重复菜谱");
        duplicate.putArray("ingredients").addObject().put("name", "番茄").put("role", "PRIMARY").put("quantity", 200).put("unit", "g").put("scalingRule", "LINEAR");
        duplicate.putArray("steps").add("切配").add("炒熟");
        var novel = mapper.createObjectNode();
        novel.put("title", "西兰花鸡胸焖饭");
        novel.put("summary", "使用库存食材的高蛋白焖饭");
        novel.put("cuisine", "家常菜").put("taste", "清淡").put("goal", "增肌").put("cookMinutes", 25).put("servings", 2);
        novel.putObject("nutrition").put("calories", 620).put("protein", 58).put("fat", 14).put("carbs", 72);
        novel.putArray("ingredients").addObject().put("name", "鸡胸肉").put("role", "PRIMARY").put("quantity", 250).put("unit", "g").put("scalingRule", "LINEAR");
        novel.withArray("ingredients").addObject().put("name", "西兰花").put("role", "SIDE").put("quantity", 180).put("unit", "g").put("scalingRule", "LINEAR");
        novel.putArray("steps").add("鸡胸肉切丁并煎至变色").add("加入米饭和西兰花焖熟");
        var output = mapper.createObjectNode();
        output.putArray("recipes").add(duplicate).add(novel);
        output.put("rationale", "避开现有菜谱生成");
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", mapper.writeValueAsString(output));
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);

        var result = new OpenAiRecipeGenerationAdapter(properties, client, mapper).discover("高蛋白晚餐",
                List.of("番茄炒蛋"), List.of(), List.of("清淡"), List.of("家常菜"), List.of("花生"), "增肌", 2000, 2);

        assertThat(result.fallback()).isFalse();
        assertThat(result.recipes()).extracting(RecipeGenerationPort.Draft::title).containsExactly("西兰花鸡胸焖饭");
    }

    @Test
    void rejectsGeneratedRecipesThatIgnoreExplicitDishAndPrimaryIngredient() throws Exception {
        AssistantProperties properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        properties.setModelName("test-model");
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        var unrelated = recipe("黑椒和牛牛排", "使用和牛牛肉煎制", "和牛牛肉", "香煎和牛牛肉");
        var aligned = recipe("红焖猪肘", "猪肘慢焖至软烂", "猪肘", "小火焖至猪肘软烂");
        var output = mapper.createObjectNode();
        output.putArray("recipes").add(unrelated).add(aligned);
        output.put("rationale", "按用户要求生成");
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", mapper.writeValueAsString(output));
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);

        var result = new OpenAiRecipeGenerationAdapter(properties, client, mapper).discover("红焖猪肘",
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null, 3);

        assertThat(result.recipes()).extracting(RecipeGenerationPort.Draft::title).containsExactly("红焖猪肘");
    }

    @Test
    void discoveryReturnsNoInventedRecipesWhenExternalAiIsDisabled() {
        var result = new OpenAiRecipeGenerationAdapter(new AssistantProperties(), mock(ExternalProviderClient.class), mapper)
                .discover("随便做一道", List.of(), List.of(), List.of(), List.of(), List.of(), null, null, 3);
        assertThat(result.fallback()).isTrue();
        assertThat(result.recipes()).isEmpty();
    }

    @Test
    void explicitBraisedGooseRejectsBeefAndKeepsGooseConstraint() throws Exception {
        AssistantProperties properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        properties.setModelName("test-model");
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        var output = mapper.createObjectNode();
        output.putArray("recipes").add(recipe("红烧牛肉", "牛肉红烧", "牛肉", "将牛肉红烧入味"))
                .add(recipe("红烧鹅", "鹅肉红烧至酥香", "鹅肉", "将鹅肉红烧并焖至软烂"));
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", mapper.writeValueAsString(output));
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);
        var result = new OpenAiRecipeGenerationAdapter(properties, client, mapper).discover("红烧鹅", List.of(), List.of(), List.of(), List.of(), List.of(), null, null, 3);
        assertThat(result.recipes()).extracting(RecipeGenerationPort.Draft::title).containsExactly("红烧鹅");
    }

    private static RecipeGenerationPort.Candidate candidate(UUID id) {
        return new RecipeGenerationPort.Candidate(id, "番茄炒蛋", "家常菜", "家常菜", "咸鲜", "均衡", 15,
                List.of("番茄", "鸡蛋"), 1, 1);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode recipe(String title, String summary, String ingredient, String step) {
        var value = mapper.createObjectNode();
        value.put("title", title).put("summary", summary).put("cookMinutes", 40).put("servings", 2);
        value.putObject("nutrition").put("calories", 800).put("protein", 50).put("fat", 40).put("carbs", 30);
        value.putArray("ingredients").addObject().put("name", ingredient).put("role", "PRIMARY")
                .put("quantity", 500).put("unit", "g").put("scalingRule", "LINEAR");
        value.putArray("steps").add("处理并腌制主料").add(step);
        return value;
    }
}
