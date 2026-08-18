package com.xianzhi.fridge.speech.config;

import com.xianzhi.fridge.speech.application.SpeechToTextPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpeechConfiguration {
    @Bean SpeechToTextPort speechToTextPort(SpeechProperties properties) {
        if (properties.isFakeEnabled()) return new SpeechToTextPort() {
            @Override public boolean available() { return true; }
            @Override public String transcribe(String objectKey) { return properties.getFakeTranscript(); }
        };
        return new SpeechToTextPort() {
            @Override public boolean available() { return false; }
            @Override public String transcribe(String objectKey) { throw new IllegalStateException("Speech provider is disabled"); }
        };
    }
}
