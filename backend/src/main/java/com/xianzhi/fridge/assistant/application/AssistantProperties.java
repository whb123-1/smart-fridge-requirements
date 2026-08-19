package com.xianzhi.fridge.assistant.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.ai")
public class AssistantProperties {
    private boolean externalCallsEnabled;
    private String modelName="rules-v1";
    private boolean vectorEnabled;
    private String baseUrl;
    private String apiKey;
    private java.time.Duration timeout=java.time.Duration.ofSeconds(30);
    private String embeddingProvider="local";
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private String embeddingModel="text-embedding-3-small";
    private int embeddingDimensions=1536;
    private String qdrantUrl="http://localhost:6333";
    private String qdrantCollection="xianzhi_recipes";
    private String qdrantApiKey;
    public boolean isExternalCallsEnabled(){return externalCallsEnabled;} public void setExternalCallsEnabled(boolean value){externalCallsEnabled=value;}
    public String getModelName(){return modelName;} public void setModelName(String value){modelName=value;}
    public boolean isVectorEnabled(){return vectorEnabled;} public void setVectorEnabled(boolean value){vectorEnabled=value;}
    public String getBaseUrl(){return baseUrl;}public void setBaseUrl(String value){baseUrl=value;}
    public String getApiKey(){return apiKey;}public void setApiKey(String value){apiKey=value;}
    public java.time.Duration getTimeout(){return timeout;}public void setTimeout(java.time.Duration value){timeout=value;}
    public String getEmbeddingProvider(){return embeddingProvider;}public void setEmbeddingProvider(String value){embeddingProvider=value;}
    public String getEmbeddingBaseUrl(){return embeddingBaseUrl;}public void setEmbeddingBaseUrl(String value){embeddingBaseUrl=value;}
    public String getEmbeddingApiKey(){return embeddingApiKey;}public void setEmbeddingApiKey(String value){embeddingApiKey=value;}
    public String getEmbeddingModel(){return embeddingModel;}public void setEmbeddingModel(String value){embeddingModel=value;}
    public int getEmbeddingDimensions(){return embeddingDimensions;}public void setEmbeddingDimensions(int value){embeddingDimensions=value;}
    public String getQdrantUrl(){return qdrantUrl;} public void setQdrantUrl(String value){qdrantUrl=value;}
    public String getQdrantCollection(){return qdrantCollection;} public void setQdrantCollection(String value){qdrantCollection=value;}
    public String getQdrantApiKey(){return qdrantApiKey;}public void setQdrantApiKey(String value){qdrantApiKey=value;}
}
