package com.xianzhi.fridge.speech.application;

public interface SpeechToTextPort {
    boolean available();
    String transcribe(String objectKey);
}
