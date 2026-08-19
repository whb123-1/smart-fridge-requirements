package com.xianzhi.fridge.assistant.application;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiAdaptersTest {
    private WireMockServer server;
    private SimpleMeterRegistry meters;
    private ObjectMapper mapper;
    private AssistantProperties properties;
    private ExternalProviderClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        meters = new SimpleMeterRegistry();
        mapper = new ObjectMapper();
        properties = new AssistantProperties();
        properties.setExternalCallsEnabled(true);
        properties.setBaseUrl(server.baseUrl() + "/v1");
        properties.setApiKey("deepseek-secret-for-test");
        properties.setModelName("deepseek-chat");
        properties.setEmbeddingBaseUrl(server.baseUrl() + "/v1");
        properties.setEmbeddingApiKey("openai-secret-for-test");
        properties.setEmbeddingModel("text-embedding-3-small");
        properties.setEmbeddingDimensions(3);
        client = new ExternalProviderClient(mapper, meters);
    }

    @AfterEach
    void tearDown() {
        server.stop();
        meters.close();
    }

    @Test
    void assistantAcceptsStructuredAnswerAndRejectsWrongSchema() {
        server.stubFor(post("/v1/chat/completions")
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":\"{\\\"answer\\\":\\\"建议先用鸡蛋\\\"}\"}}]}")));
        var adapter = new OpenAiAssistantGenerationAdapter(properties, client, mapper);

        var generated = adapter.generate("做什么菜", "inventory", "{}", "规则降级");

        assertThat(generated.answer()).isEqualTo("建议先用鸡蛋");
        assertThat(generated.fallback()).isFalse();
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer deepseek-secret-for-test"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("deepseek-chat"))));

        server.resetAll();
        server.stubFor(post("/v1/chat/completions")
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":\"{\\\"unexpected\\\":true}\"}}]}")));
        var fallback = adapter.generate("做什么菜", "inventory", "{}", "规则降级");
        assertThat(fallback.answer()).isEqualTo("规则降级");
        assertThat(fallback.fallback()).isTrue();
    }

    @Test
    void embeddingRequiresExactConfiguredDimensions() {
        var adapter = new OpenAiEmbeddingAdapter(properties, client, mapper);
        server.stubFor(post("/v1/embeddings")
                .willReturn(okJson("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}")));
        assertThat(adapter.embed("番茄")).containsExactly(0.1f, 0.2f, 0.3f);
        server.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
                .withHeader("Authorization", equalTo("Bearer openai-secret-for-test"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("text-embedding-3-small")))
                .withRequestBody(matchingJsonPath("$.dimensions", equalTo("3"))));

        server.resetAll();
        server.stubFor(post("/v1/embeddings")
                .willReturn(okJson("{\"data\":[{\"embedding\":[0.1,0.2]}]}")));
        assertThatThrownBy(() -> adapter.embed("番茄"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimension mismatch");
    }
}
