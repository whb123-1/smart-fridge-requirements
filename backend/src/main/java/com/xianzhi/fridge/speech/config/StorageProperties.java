package com.xianzhi.fridge.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.storage")
public class StorageProperties {
    private String provider="local";private String endpoint;private String region="us-east-1";private String bucket="xianzhi-speech";
    private String accessKey;private String secretKey;private boolean pathStyle=true;private boolean serverSideEncryption=true;
    private int lifecycleDays=2;private boolean manageLifecycle=true;
    public String getProvider(){return provider;}public void setProvider(String v){provider=v;}
    public String getEndpoint(){return endpoint;}public void setEndpoint(String v){endpoint=v;}
    public String getRegion(){return region;}public void setRegion(String v){region=v;}
    public String getBucket(){return bucket;}public void setBucket(String v){bucket=v;}
    public String getAccessKey(){return accessKey;}public void setAccessKey(String v){accessKey=v;}
    public String getSecretKey(){return secretKey;}public void setSecretKey(String v){secretKey=v;}
    public boolean isPathStyle(){return pathStyle;}public void setPathStyle(boolean v){pathStyle=v;}
    public boolean isServerSideEncryption(){return serverSideEncryption;}public void setServerSideEncryption(boolean v){serverSideEncryption=v;}
    public int getLifecycleDays(){return lifecycleDays;}public void setLifecycleDays(int v){lifecycleDays=v;}
    public boolean isManageLifecycle(){return manageLifecycle;}public void setManageLifecycle(boolean v){manageLifecycle=v;}
}
