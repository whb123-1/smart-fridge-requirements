package com.xianzhi.fridge.speech.config;

import com.xianzhi.fridge.speech.application.SpeechToTextPort;
import com.xianzhi.fridge.speech.application.ObjectStoragePort;
import com.xianzhi.fridge.speech.infrastructure.OpenAiSpeechToTextAdapter;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpeechConfiguration {
    @Bean SpeechToTextPort speechToTextPort(SpeechProperties properties,ObjectStoragePort storage,ExternalProviderClient client) {
        if (properties.isFakeEnabled()||"fake".equalsIgnoreCase(properties.getProvider())) return new SpeechToTextPort() {
            @Override public boolean available() { return true; }
            @Override public String transcribe(String objectKey) { return properties.getFakeTranscript(); }
        };
        if("openai".equalsIgnoreCase(properties.getProvider()))return new OpenAiSpeechToTextAdapter(properties,storage,client);
        return new SpeechToTextPort() {
            @Override public boolean available() { return false; }
            @Override public String transcribe(String objectKey) { throw new IllegalStateException("Speech provider is disabled"); }
        };
    }
}
