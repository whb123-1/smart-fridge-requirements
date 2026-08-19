package com.xianzhi.fridge.speech.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import com.xianzhi.fridge.speech.application.ObjectStoragePort;
import com.xianzhi.fridge.speech.config.SpeechProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class OpenAiSpeechToTextAdapterTest {
    private WireMockServer server;
    private SimpleMeterRegistry meters;
    private OpenAiSpeechToTextAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        meters = new SimpleMeterRegistry();
        SpeechProperties properties = new SpeechProperties();
        properties.setBaseUrl(server.baseUrl() + "/v1");
        properties.setApiKey("speech-secret");
        ObjectStoragePort storage = new ObjectStoragePort() {
            @Override public String store(UUID userId, UUID ingestionId, MultipartFile file) { return "audio"; }
            @Override public java.io.InputStream open(String key) { return new ByteArrayInputStream("audio".getBytes(StandardCharsets.UTF_8)); }
            @Override public void delete(String key) { }
        };
        adapter = new OpenAiSpeechToTextAdapter(properties, storage,
                new ExternalProviderClient(new ObjectMapper(), meters));
    }

    @AfterEach
    void tearDown() {
        server.stop();
        meters.close();
    }

    @Test
    void streamsAudioAndReturnsTranscript() {
        server.stubFor(post("/v1/audio/transcriptions").willReturn(okJson("{\"text\":\"鸡蛋两个\"}")));
        assertThat(adapter.transcribe("speech/object")).isEqualTo("鸡蛋两个");
        server.verify(postRequestedFor(urlEqualTo("/v1/audio/transcriptions"))
                .withHeader("Authorization", equalTo("Bearer speech-secret"))
                .withHeader("Content-Type", containing("multipart/form-data")));
    }

    @Test
    void rejectsEmptyTranscriptSchema() {
        server.stubFor(post("/v1/audio/transcriptions").willReturn(okJson("{\"unexpected\":true}")));
        assertThatThrownBy(() -> adapter.transcribe("speech/object"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no transcript");
    }
}
