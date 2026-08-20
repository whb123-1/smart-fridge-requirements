package com.xianzhi.fridge.nutrition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.nutrition.api.MealContracts;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import com.xianzhi.fridge.shared.web.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OpenAiMealNutritionEstimatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsValidatedStructuredAiNutrition() throws Exception {
        AssistantProperties properties = configuredProperties();
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        var output = mapper.createObjectNode().put("calories", 428).put("protein", 31.5).put("fat", 14).put("carbs", 42);
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", mapper.writeValueAsString(output));
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);

        var result = new OpenAiMealNutritionEstimator(properties, client, mapper)
                .estimate(new MealContracts.EstimateRequest("鸡胸肉豆腐煲", new BigDecimal("350"), "g"));

        assertThat(result.calories()).isEqualByComparingTo("428");
        assertThat(result.protein()).isEqualByComparingTo("31.5");
        assertThat(result.estimated()).isTrue();
        assertThat(result.source()).isEqualTo("AI_ESTIMATE:test-model");
    }

    @Test
    void refusesToReplaceAiWithRulesWhenProviderIsDisabled() {
        AssistantProperties properties = new AssistantProperties();
        var estimator = new OpenAiMealNutritionEstimator(properties, mock(ExternalProviderClient.class), mapper);

        assertThatThrownBy(() -> estimator.estimate(new MealContracts.EstimateRequest("炒饭", null, "serving")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("AI_NUTRITION_UNAVAILABLE");
    }

    @Test
    void rejectsMalformedProviderOutput() {
        AssistantProperties properties = configuredProperties();
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", "{\"calories\":-1}");
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);

        assertThatThrownBy(() -> new OpenAiMealNutritionEstimator(properties, client, mapper)
                .estimate(new MealContracts.EstimateRequest("炒饭", null, "serving")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("AI_NUTRITION_UNAVAILABLE");
    }

    private static AssistantProperties configuredProperties() {
        AssistantProperties properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        properties.setApiKey("test-key");
        properties.setModelName("test-model");
        return properties;
    }
}
