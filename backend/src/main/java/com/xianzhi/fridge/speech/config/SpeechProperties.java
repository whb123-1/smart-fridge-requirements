package com.xianzhi.fridge.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.speech")
public class SpeechProperties {
    private boolean fakeEnabled;
    private String fakeTranscript = "鸡蛋 2 个";
    private String storagePath = System.getProperty("java.io.tmpdir") + "/xianzhi-speech";
    private long maxUploadBytes = 10 * 1024 * 1024;

    public boolean isFakeEnabled() { return fakeEnabled; }
    public void setFakeEnabled(boolean fakeEnabled) { this.fakeEnabled = fakeEnabled; }
    public String getFakeTranscript() { return fakeTranscript; }
    public void setFakeTranscript(String fakeTranscript) { this.fakeTranscript = fakeTranscript; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
}
