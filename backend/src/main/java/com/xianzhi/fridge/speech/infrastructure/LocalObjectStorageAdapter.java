package com.xianzhi.fridge.speech.infrastructure;

import com.xianzhi.fridge.speech.application.ObjectStoragePort;
import com.xianzhi.fridge.speech.config.SpeechProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name="app.storage.provider",havingValue="local",matchIfMissing=true)
public class LocalObjectStorageAdapter implements ObjectStoragePort {
    private final Path root;
    public LocalObjectStorageAdapter(SpeechProperties properties) { this.root = Path.of(properties.getStoragePath()).toAbsolutePath().normalize(); }
    @Override public String store(UUID userId, UUID ingestionId, MultipartFile file) throws IOException {
        Path target = root.resolve(userId.toString()).resolve(ingestionId + ".audio").normalize();
        if (!target.startsWith(root)) throw new IOException("Invalid speech object path");
        Files.createDirectories(target.getParent());
        try (java.io.InputStream input = file.getInputStream()) { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); }
        return target.toString();
    }
    @Override public java.io.InputStream open(String objectKey) throws IOException {
        Path target=Path.of(objectKey).toAbsolutePath().normalize();
        if(!target.startsWith(root))throw new IOException("Invalid speech object path");
        return Files.newInputStream(target);
    }
    @Override public void delete(String objectKey) {
        try {
            Path target = Path.of(objectKey).toAbsolutePath().normalize();
            if (target.startsWith(root)) Files.deleteIfExists(target);
        } catch (IOException ignored) { }
    }
}
