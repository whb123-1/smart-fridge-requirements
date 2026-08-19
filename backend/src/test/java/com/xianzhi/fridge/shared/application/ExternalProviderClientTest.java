package com.xianzhi.fridge.shared.application;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalProviderClientTest {
    private WireMockServer server;
    private SimpleMeterRegistry meters;
    private ExternalProviderClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        meters = new SimpleMeterRegistry();
        client = new ExternalProviderClient(mapper, meters);
    }

    @AfterEach
    void stopServer() {
        server.stop();
        meters.close();
    }

    @Test
    void retries429AndThenReturnsValidatedJson() {
        server.stubFor(post("/retry").inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willSetStateTo("available")
                .willReturn(aResponse().withStatus(429)));
        server.stubFor(post("/retry").inScenario("rate-limit")
                .whenScenarioStateIs("available")
                .willReturn(okJson("{\"ok\":true}")));

        var response = client.postJson("retry-provider", server.baseUrl() + "/retry", "secret",
                mapper.createObjectNode().put("input", "hello"), Duration.ofSeconds(1));

        assertThat(response.path("ok").asBoolean()).isTrue();
        server.verify(2, postRequestedFor(urlEqualTo("/retry"))
                .withHeader("Authorization", equalTo("Bearer secret")));
        assertThat(meters.counter("xianzhi.provider.calls", "provider", "retry-provider", "outcome", "success").count())
                .isEqualTo(1);
    }

    @Test
    void exhaustsRetriesFor5xxAndRecordsFailure() {
        server.stubFor(post("/failure").willReturn(serverError()));

        assertThatThrownBy(() -> client.postJson("failing-provider", server.baseUrl() + "/failure", "",
                mapper.createObjectNode(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");

        server.verify(3, postRequestedFor(urlEqualTo("/failure")));
        assertThat(meters.counter("xianzhi.provider.calls", "provider", "failing-provider", "outcome", "failure").count())
                .isEqualTo(1);
    }

    @Test
    void enforcesRequestTimeout() {
        server.stubFor(post("/slow").willReturn(okJson("{}").withFixedDelay(250)));

        assertThatThrownBy(() -> client.postJson("slow-provider", server.baseUrl() + "/slow", "",
                mapper.createObjectNode(), Duration.ofMillis(40)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request failed");
    }

    @Test
    void rejectsNonHttpProviderUrlsBeforeSendingSecrets() {
        assertThatThrownBy(() -> client.postJson("invalid-provider", "file:///tmp/provider", "secret",
                mapper.createObjectNode(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP(S)");
    }

    @Test
    void doesNotExposeProviderResponseOrApiKeyInErrors() {
        String secret = "provider-secret-that-must-not-leak";
        server.stubFor(post("/unauthorized").willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"rejected " + secret + "\"}")));

        assertThatThrownBy(() -> client.postJson("redacted-provider", server.baseUrl() + "/unauthorized", secret,
                mapper.createObjectNode(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redacted-provider returned HTTP 401")
                .hasMessageNotContaining(secret)
                .hasMessageNotContaining("rejected");
    }

    @Test
    void opensCircuitAfterRepeatedProviderFailures() {
        server.stubFor(post("/circuit").willReturn(aResponse().withStatus(503)));
        for (int call = 0; call < 5; call++) {
            assertThatThrownBy(() -> client.postJson("circuit-provider", server.baseUrl() + "/circuit", "",
                    mapper.createObjectNode(), Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HTTP 503");
        }

        assertThatThrownBy(() -> client.postJson("circuit-provider", server.baseUrl() + "/circuit", "",
                mapper.createObjectNode(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("circuit-provider circuit is open");
        server.verify(15, postRequestedFor(urlEqualTo("/circuit")));
    }
}
