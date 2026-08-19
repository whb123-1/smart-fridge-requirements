package com.xianzhi.fridge.speech.infrastructure;

import com.xianzhi.fridge.speech.application.ObjectStoragePort;
import com.xianzhi.fridge.speech.config.StorageProperties;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(name="app.storage.provider",havingValue="s3")
public class S3ObjectStorageAdapter implements ObjectStoragePort {
    private final StorageProperties properties;private final S3Client client;
    public S3ObjectStorageAdapter(StorageProperties properties){
        this.properties=properties;var builder=S3Client.builder().region(Region.of(properties.getRegion()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.isPathStyle()).build());
        if(properties.getEndpoint()!=null&&!properties.getEndpoint().isBlank())builder.endpointOverride(URI.create(properties.getEndpoint()));
        if(properties.getAccessKey()!=null&&!properties.getAccessKey().isBlank())builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKey(),properties.getSecretKey())));
        this.client=builder.build();
    }
    @PostConstruct void configureLifecycle(){if(!properties.isManageLifecycle())return;LifecycleRule rule=LifecycleRule.builder().id("expire-speech-objects").status(ExpirationStatus.ENABLED).filter(LifecycleRuleFilter.builder().prefix("speech/").build()).expiration(LifecycleExpiration.builder().days(properties.getLifecycleDays()).build()).build();client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder().bucket(properties.getBucket()).lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(rule).build()).build());}
    @Override public String store(UUID userId,UUID ingestionId,MultipartFile file)throws IOException{
        String key="speech/"+userId+"/"+ingestionId+".audio";
        PutObjectRequest.Builder request=PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(file.getContentType()).contentLength(file.getSize());
        if(properties.isServerSideEncryption())request.serverSideEncryption(ServerSideEncryption.AES256);
        try(var input=file.getInputStream()){client.putObject(request.build(),RequestBody.fromInputStream(input,file.getSize()));return key;}
        catch(S3Exception exception){throw new IOException("S3 upload failed",exception);}
    }
    @Override public java.io.InputStream open(String key)throws IOException{try{return client.getObject(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build());}catch(S3Exception e){throw new IOException("S3 read failed",e);}}
    @Override public void delete(String key){try{client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());}catch(S3Exception ignored){}}
}
