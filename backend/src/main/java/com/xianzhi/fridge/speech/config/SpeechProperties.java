package com.xianzhi.fridge.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.speech")
public class SpeechProperties {
    private String provider="disabled";
    private boolean fakeEnabled;
    private String fakeTranscript = "鸡蛋 2 个";
    private String storagePath = System.getProperty("java.io.tmpdir") + "/xianzhi-speech";
    private long maxUploadBytes = 10 * 1024 * 1024;
    private String baseUrl;private String apiKey;private String model="whisper-1";private String language="zh";
    private java.time.Duration timeout=java.time.Duration.ofSeconds(60);

    public String getProvider(){return provider;}public void setProvider(String value){provider=value;}

    public boolean isFakeEnabled() { return fakeEnabled; }
    public void setFakeEnabled(boolean fakeEnabled) { this.fakeEnabled = fakeEnabled; }
    public String getFakeTranscript() { return fakeTranscript; }
    public void setFakeTranscript(String fakeTranscript) { this.fakeTranscript = fakeTranscript; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
    public String getBaseUrl(){return baseUrl;}public void setBaseUrl(String value){baseUrl=value;}
    public String getApiKey(){return apiKey;}public void setApiKey(String value){apiKey=value;}
    public String getModel(){return model;}public void setModel(String value){model=value;}
    public String getLanguage(){return language;}public void setLanguage(String value){language=value;}
    public java.time.Duration getTimeout(){return timeout;}public void setTimeout(java.time.Duration value){timeout=value;}
}
