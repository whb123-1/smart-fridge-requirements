package com.xianzhi.fridge.speech.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ObjectStoragePort {
    String store(UUID userId, UUID ingestionId, MultipartFile file) throws IOException;
    InputStream open(String objectKey) throws IOException;
    void delete(String objectKey);
}
