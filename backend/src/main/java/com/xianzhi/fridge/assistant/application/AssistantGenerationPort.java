package com.xianzhi.fridge.assistant.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface AssistantGenerationPort {
    GeneratedAnswer generate(String userMessage,String page,String contextJson);
    record SuggestedAction(String command,String title,JsonNode arguments){}
    record GeneratedAnswer(String answer,String model,boolean fallback,SuggestedAction action){}
}
