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
        AppProperties app=new AppProperties();app.setPublicUrl("https://fridge.example.com");
        app.getSecurity().setRefreshCookieSecure(true);
        app.getSecurity().setJwtSigningKey("j".repeat(64));
        app.getIdentity().setTombstoneKey("t".repeat(48));
        SpeechProperties speech=new SpeechProperties();speech.setProvider("disabled");speech.setFakeEnabled(false);
        StorageProperties storage=new StorageProperties();storage.setProvider("disabled");
        MockEnvironment environment=new MockEnvironment().withProperty("spring.datasource.password","database-password-strong");
        environment.setActiveProfiles("prod");
        var validator=new ProductionConfigurationValidator(app,new TelemetryProperties(),speech,storage,
                new AssistantProperties(),environment);
        assertThatCode(()->validator.run(null)).doesNotThrowAnyException();
    }
}
