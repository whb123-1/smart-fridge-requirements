package com.xianzhi.fridge.assistant.application;

public interface AssistantGenerationPort {
    GeneratedAnswer generate(String userMessage,String page,String contextJson);
    record GeneratedAnswer(String answer,String model,boolean fallback){}
}
