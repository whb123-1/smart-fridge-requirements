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

    private static RecipeGenerationPort.Candidate candidate(UUID id) {
        return new RecipeGenerationPort.Candidate(id, "番茄炒蛋", "家常菜", "家常菜", "咸鲜", "均衡", 15,
                List.of("番茄", "鸡蛋"), 1, 1);
    }
}
