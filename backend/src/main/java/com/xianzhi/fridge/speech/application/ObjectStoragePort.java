package com.xianzhi.fridge.speech.application;

import java.io.IOException;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ObjectStoragePort {
    String store(UUID userId, UUID ingestionId, MultipartFile file) throws IOException;
    void delete(String objectKey);
}
