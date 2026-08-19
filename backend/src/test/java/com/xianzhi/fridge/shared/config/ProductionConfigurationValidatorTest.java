package com.xianzhi.fridge.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.speech.config.SpeechProperties;
import com.xianzhi.fridge.speech.config.StorageProperties;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {
    @Test
    void rejectsDevelopmentDefaultsInProduction() {
        AppProperties app=new AppProperties();
        MockEnvironment environment=new MockEnvironment().withProperty("spring.datasource.password","change-me");
        environment.setActiveProfiles("prod");
        var validator=new ProductionConfigurationValidator(app,new TelemetryProperties(),new SpeechProperties(),
                new StorageProperties(),new AssistantProperties(),environment);
        assertThatThrownBy(()->validator.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe production configuration");
    }

    @Test
    void acceptsHardenedConfigurationWithOptionalProvidersDisabled() {
        AppProperties app=hardenedApp();
        SpeechProperties speech=new SpeechProperties();speech.setProvider("disabled");speech.setFakeEnabled(false);
        StorageProperties storage=new StorageProperties();storage.setProvider("disabled");
        MockEnvironment environment=hardenedEnvironment();
        var validator=new ProductionConfigurationValidator(app,new TelemetryProperties(),speech,storage,
                new AssistantProperties(),environment);
        assertThatCode(()->validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsInternalVirtualProbeTransportWithoutAClientBrokerUrl() {
        AppProperties app=hardenedApp();
        TelemetryProperties telemetry=new TelemetryProperties();
        telemetry.setEnabled(true);
        telemetry.setServicePassword("m".repeat(24));
        telemetry.setInternalToken("i".repeat(40));
        SpeechProperties speech=new SpeechProperties();speech.setProvider("disabled");speech.setFakeEnabled(false);
        StorageProperties storage=new StorageProperties();storage.setProvider("disabled");

        var validator=new ProductionConfigurationValidator(app,telemetry,speech,storage,
                new AssistantProperties(),hardenedEnvironment());

        assertThatCode(()->validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsDeepSeekChatAndOpenAiSpeechAndEmbeddingsWithMinio() {
        ProviderFixture fixture=providerFixture();

        assertThatCode(()->fixture.validator().run(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingIndependentProviderSecrets() {
        ProviderFixture fixture=providerFixture();
        fixture.ai().setApiKey("");
        fixture.speech().setApiKey("");
        fixture.ai().setEmbeddingApiKey("");

        assertThatThrownBy(()->fixture.validator().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeepSeek API key")
                .hasMessageContaining("OpenAI speech API key")
                .hasMessageContaining("OpenAI embedding API key");
    }

    @Test
    void rejectsVectorSearchWithoutExternalCalls() {
        ProviderFixture fixture=providerFixture();
        fixture.ai().setExternalCallsEnabled(false);

        assertThatThrownBy(()->fixture.validator().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_VECTOR_ENABLED requires AI_EXTERNAL_CALLS_ENABLED");
    }

    @Test
    void rejectsUnapprovedProductionModelsAndEmbeddingDimensions() {
        ProviderFixture fixture=providerFixture();
        fixture.ai().setModelName("unexpected-chat-model");
        fixture.speech().setModel("unexpected-speech-model");
        fixture.ai().setEmbeddingModel("unexpected-embedding-model");
        fixture.ai().setEmbeddingDimensions(64);

        assertThatThrownBy(()->fixture.validator().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_MODEL_NAME must be deepseek-chat")
                .hasMessageContaining("SPEECH_MODEL must be whisper-1")
                .hasMessageContaining("EMBEDDING_MODEL must be text-embedding-3-small")
                .hasMessageContaining("EMBEDDING_DIMENSIONS must be 1536");
    }

    private static ProviderFixture providerFixture() {
        SpeechProperties speech=new SpeechProperties();
        speech.setProvider("openai");
        speech.setBaseUrl("https://api.openai.com/v1");
        speech.setApiKey("openai-secret-for-speech-and-embedding");
        speech.setModel("whisper-1");

        StorageProperties storage=new StorageProperties();
        storage.setProvider("s3");
        storage.setEndpoint("http://minio:9000");
        storage.setBucket("xianzhi-speech");
        storage.setAccessKey("minio-access");
        storage.setSecretKey("minio-secret-password-for-test");
        storage.setServerSideEncryption(true);

        AssistantProperties ai=new AssistantProperties();
        ai.setExternalCallsEnabled(true);
        ai.setBaseUrl("https://api.deepseek.com/v1");
        ai.setApiKey("deepseek-secret-for-chat-test");
        ai.setModelName("deepseek-chat");
        ai.setVectorEnabled(true);
        ai.setEmbeddingProvider("openai");
        ai.setEmbeddingBaseUrl("https://api.openai.com/v1");
        ai.setEmbeddingApiKey("openai-secret-for-speech-and-embedding");
        ai.setEmbeddingModel("text-embedding-3-small");
        ai.setEmbeddingDimensions(1536);
        ai.setQdrantUrl("http://qdrant:6333");
        ai.setQdrantApiKey("qdrant-secret-password-for-test");

        var validator=new ProductionConfigurationValidator(hardenedApp(),new TelemetryProperties(),speech,storage,ai,
                hardenedEnvironment());
        return new ProviderFixture(validator,speech,ai);
    }

    private static AppProperties hardenedApp() {
        AppProperties app=new AppProperties();app.setPublicUrl("https://fridge.example.com");
        app.getSecurity().setRefreshCookieSecure(true);
        app.getSecurity().setJwtSigningKey("j".repeat(64));
        app.getIdentity().setTombstoneKey("t".repeat(48));
        return app;
    }

    private static MockEnvironment hardenedEnvironment() {
        MockEnvironment environment=new MockEnvironment().withProperty("spring.datasource.password","database-password-strong");
        environment.setActiveProfiles("prod");
        return environment;
    }

    private record ProviderFixture(ProductionConfigurationValidator validator,SpeechProperties speech,
                                   AssistantProperties ai) {}
}
