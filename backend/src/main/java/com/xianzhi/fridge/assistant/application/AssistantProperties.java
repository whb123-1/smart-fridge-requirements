package com.xianzhi.fridge.assistant.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.ai")
public class AssistantProperties {
    private boolean externalCallsEnabled;
    private String modelName="rules-v1";
    private boolean vectorEnabled;
    private String qdrantUrl="http://localhost:6333";
    private String qdrantCollection="xianzhi_recipes";
    public boolean isExternalCallsEnabled(){return externalCallsEnabled;} public void setExternalCallsEnabled(boolean value){externalCallsEnabled=value;}
    public String getModelName(){return modelName;} public void setModelName(String value){modelName=value;}
    public boolean isVectorEnabled(){return vectorEnabled;} public void setVectorEnabled(boolean value){vectorEnabled=value;}
    public String getQdrantUrl(){return qdrantUrl;} public void setQdrantUrl(String value){qdrantUrl=value;}
    public String getQdrantCollection(){return qdrantCollection;} public void setQdrantCollection(String value){qdrantCollection=value;}
}
