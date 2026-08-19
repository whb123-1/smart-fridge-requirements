package com.xianzhi.fridge.speech.infrastructure;

import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import com.xianzhi.fridge.speech.application.ObjectStoragePort;
import com.xianzhi.fridge.speech.application.SpeechToTextPort;
import com.xianzhi.fridge.speech.config.SpeechProperties;

public class OpenAiSpeechToTextAdapter implements SpeechToTextPort {
    private final SpeechProperties properties;private final ObjectStoragePort storage;private final ExternalProviderClient client;
    public OpenAiSpeechToTextAdapter(SpeechProperties properties,ObjectStoragePort storage,ExternalProviderClient client){this.properties=properties;this.storage=storage;this.client=client;}
    @Override public boolean available(){return properties.getBaseUrl()!=null&&!properties.getBaseUrl().isBlank()&&properties.getModel()!=null&&!properties.getModel().isBlank();}
    @Override public String transcribe(String objectKey){try{
        var response=client.postAudio("speech",endpoint(properties.getBaseUrl(),"/audio/transcriptions"),properties.getApiKey(),()->storage.open(objectKey),properties.getModel(),properties.getLanguage(),properties.getTimeout());
        String text=response.path("text").asText("").trim();if(text.isBlank())throw new IllegalStateException("Speech provider returned no transcript");return text;
    }catch(RuntimeException exception){throw exception;}}
    private static String endpoint(String base,String path){String value=base.replaceAll("/$","");return value.endsWith(path)?value:value+path;}
}
