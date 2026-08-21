package com.xianzhi.fridge.recipe.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TavilyWebRecipeSearchAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExternalProviderClient client = mock(ExternalProviderClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-21T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void keepsPublicSourcesAndRejectsPrivateUrls() {
        var response = mapper.createObjectNode();
        var results = response.putArray("results");
        results.addObject().put("title", "红烧鹅做法").put("url", "https://example.com/goose").put("content", "鹅肉焯水后红烧并小火焖熟");
        results.addObject().put("title", "内部地址").put("url", "http://127.0.0.1/admin").put("content", "不可使用");
        when(client.postJsonLimited(any(), any(), any(), any(), any(), anyInt())).thenReturn(response);
        var result = adapter().search("红烧鹅");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().url()).isEqualTo("https://example.com/goose");
        assertThat(result.sources().getFirst().retrievedAt()).isEqualTo(clock.instant());
        assertThat(result.warnings()).contains("已忽略不安全的来源地址");
    }

    @Test
    void boundsExternalSummaryAndFallsBackOnProviderFailure() {
        var response = mapper.createObjectNode();
        response.putArray("results").addObject().put("title", "来源").put("url", "https://example.com/recipe").put("content", "a".repeat(9000));
        when(client.postJsonLimited(any(), any(), any(), any(), any(), anyInt())).thenReturn(response).thenThrow(new IllegalStateException("timeout"));
        assertThat(adapter().search("菜谱").sources().getFirst().summary()).hasSize(4000);
        var failed = adapter().search("菜谱");
        assertThat(failed.fallback()).isTrue();
        assertThat(failed.warnings()).contains("联网搜索暂时不可用，本次未生成菜谱草稿");
    }

    @Test
    void reportsDisabledCapabilityWithoutInventingResults() {
        AssistantProperties properties = new AssistantProperties();
        var result = new TavilyWebRecipeSearchAdapter(properties, client, mapper, clock).search("红烧鹅");
        assertThat(result.sources()).isEmpty();
        assertThat(result.fallback()).isTrue();
        assertThat(result.warnings().getFirst()).contains("TAVILY_API_KEY");
    }

    @Test
    void blocksNonHttpAndPrivateNetworkUrls() {
        assertThat(TavilyWebRecipeSearchAdapter.publicHttpUrl("file:///etc/passwd")).isFalse();
        assertThat(TavilyWebRecipeSearchAdapter.publicHttpUrl("http://10.0.0.1/recipe")).isFalse();
        assertThat(TavilyWebRecipeSearchAdapter.publicHttpUrl("http://169.254.169.254/latest/meta-data")).isFalse();
    }

    private TavilyWebRecipeSearchAdapter adapter() {
        AssistantProperties properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setTavilyApiKey("test-key");
        properties.setTavilyBaseUrl("https://api.tavily.com");
        properties.setTavilyMaxResults(5);
        return new TavilyWebRecipeSearchAdapter(properties, client, mapper, clock);
    }
}
