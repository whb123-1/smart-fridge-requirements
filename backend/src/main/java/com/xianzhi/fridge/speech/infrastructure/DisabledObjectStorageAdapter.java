package com.xianzhi.fridge.speech.infrastructure;

import com.xianzhi.fridge.speech.application.ObjectStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name="app.storage.provider",havingValue="disabled")
public class DisabledObjectStorageAdapter implements ObjectStoragePort {
    @Override public String store(UUID userId,UUID ingestionId,MultipartFile file)throws IOException{throw new IOException("Object storage is disabled");}
    @Override public InputStream open(String objectKey)throws IOException{throw new IOException("Object storage is disabled");}
    @Override public void delete(String objectKey){ }
}
