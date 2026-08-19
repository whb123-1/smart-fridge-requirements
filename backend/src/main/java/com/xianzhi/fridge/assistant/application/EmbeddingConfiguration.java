package com.xianzhi.fridge.assistant.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfiguration {
    @Bean EmbeddingPort embeddingPort(AssistantProperties properties,ExternalProviderClient client,ObjectMapper mapper){
        if("openai".equalsIgnoreCase(properties.getEmbeddingProvider()))return new OpenAiEmbeddingAdapter(properties,client,mapper);
        if("local".equalsIgnoreCase(properties.getEmbeddingProvider()))return new EmbeddingPort(){
            public boolean available(){return true;}public int dimensions(){return 64;}
            public float[] embed(String value){float[] vector=new float[64];byte[] bytes=value.toLowerCase().getBytes(StandardCharsets.UTF_8);for(int i=0;i<bytes.length;i++){int slot=Math.floorMod((bytes[i]&0xff)*31+i*17,64);vector[slot]+=1f;}double norm=0;for(float v:vector)norm+=v*v;if(norm>0){float d=(float)Math.sqrt(norm);for(int i=0;i<vector.length;i++)vector[i]/=d;}return vector;}
        };
        return new EmbeddingPort(){public boolean available(){return false;}public int dimensions(){return 0;}public float[] embed(String text){throw new IllegalStateException("Embedding is disabled");}};
    }
}
