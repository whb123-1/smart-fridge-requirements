package com.xianzhi.fridge.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.domain.FoodCategory;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OpenAiShelfLifeEstimatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsStructuredAiEstimateWithinProfileSafetyBounds() throws Exception {
        AssistantProperties properties = properties();
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        var output = mapper.createObjectNode().put("shelfLifeHours", 96).put("confidence", "HIGH").put("reason", "结合叶菜、保鲜区和未开封状态");
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", mapper.writeValueAsString(output));
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);
        GlobalShelfLifeEstimator fallback = mock(GlobalShelfLifeEstimator.class);
        Instant storedAt = Instant.parse("2026-08-20T00:00:00Z");

        var estimate = new OpenAiShelfLifeEstimator(properties, client, mapper, fallback)
                .estimate(item(), batch(storedAt), null, profile(120), "FRESH", storedAt.plusSeconds(3600));

        assertThat(estimate.source()).isEqualTo(AssessmentSource.AI_GLOBAL_MODEL);
        assertThat(estimate.baseExpiryAt()).isEqualTo(storedAt.plusSeconds(96L * 3600));
        assertThat(estimate.confidence()).isEqualTo("HIGH");
    }

    @Test
    void rejectsUnsafeAiDurationAndUsesHonestFallback() throws Exception {
        AssistantProperties properties = properties();
        ExternalProviderClient client = mock(ExternalProviderClient.class);
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", "{\"shelfLifeHours\":9999}");
        when(client.postJson(any(), any(), any(), any(), any())).thenReturn(response);
        GlobalShelfLifeEstimator fallback = mock(GlobalShelfLifeEstimator.class);
        ShelfLifeEstimator.Estimate expected = new ShelfLifeEstimator.Estimate(Instant.parse("2026-08-25T00:00:00Z"), AssessmentSource.CATALOG_PROFILE, "MEDIUM", "目录档案估算");
        when(fallback.estimate(any(), any(), any(), any(), any(), any())).thenReturn(expected);

        var estimate = new OpenAiShelfLifeEstimator(properties, client, mapper, fallback)
                .estimate(item(), batch(Instant.parse("2026-08-20T00:00:00Z")), null, profile(120), "FRESH", Instant.parse("2026-08-20T01:00:00Z"));

        assertThat(estimate).isEqualTo(expected);
        assertThat(estimate.source()).isNotEqualTo(AssessmentSource.AI_GLOBAL_MODEL);
    }

    private static AssistantProperties properties(){AssistantProperties value=new AssistantProperties();value.setExternalCallsEnabled(true);value.setBaseUrl("https://example.test/v1");value.setModelName("test-model");return value;}
    private static InventoryItem item(){return new InventoryItem(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"上海青",FoodCategory.VEGETABLE,null,"g");}
    private static InventoryBatch batch(Instant storedAt){InventoryItem item=item();return new InventoryBatch(UUID.randomUUID(),item.getId(),UUID.randomUUID(),storedAt,null,null,null,new BigDecimal("300"),"g",null);}
    private static FoodStorageProfile profile(int hours)throws Exception{var constructor=FoodStorageProfile.class.getDeclaredConstructor();constructor.setAccessible(true);FoodStorageProfile value=constructor.newInstance();ReflectionTestUtils.setField(value,"unopenedHours",hours);ReflectionTestUtils.setField(value,"openedHours",72);ReflectionTestUtils.setField(value,"riskCoefficient",BigDecimal.ONE);return value;}
}
