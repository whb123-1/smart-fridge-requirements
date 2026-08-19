package com.xianzhi.fridge.assistant.application;

public interface EmbeddingPort {
    boolean available();
    int dimensions();
    float[] embed(String text);
}
