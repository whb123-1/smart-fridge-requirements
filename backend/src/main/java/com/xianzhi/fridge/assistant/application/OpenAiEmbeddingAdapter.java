package com.xianzhi.fridge.assistant.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.shared.application.ExternalProviderClient;

public class OpenAiEmbeddingAdapter implements EmbeddingPort {
    private final AssistantProperties properties;private final ExternalProviderClient client;private final ObjectMapper mapper;
    public OpenAiEmbeddingAdapter(AssistantProperties properties,ExternalProviderClient client,ObjectMapper mapper){this.properties=properties;this.client=client;this.mapper=mapper;}
    public boolean available(){return properties.isExternalCallsEnabled()&&properties.getEmbeddingBaseUrl()!=null&&!properties.getEmbeddingBaseUrl().isBlank();}
    public int dimensions(){return properties.getEmbeddingDimensions();}
    public float[] embed(String text){if(!available())throw new IllegalStateException("Embedding provider is disabled");var body=mapper.createObjectNode().put("model",properties.getEmbeddingModel()).put("input",text).put("dimensions",dimensions());var response=client.postJson("openai-embedding",endpoint(properties.getEmbeddingBaseUrl(),"/embeddings"),properties.getEmbeddingApiKey(),body,properties.getTimeout());var values=response.path("data").path(0).path("embedding");if(!values.isArray()||values.size()!=dimensions())throw new IllegalStateException("Embedding dimension mismatch");float[] result=new float[values.size()];for(int i=0;i<values.size();i++)result[i]=(float)values.get(i).asDouble();return result;}
    private static String endpoint(String base,String path){String value=base.replaceAll("/$","");return value.endsWith(path)?value:value+path;}
}
