package com.xianzhi.fridge.speech.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.xianzhi.fridge.speech.config.StorageProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Testcontainers(disabledWithoutDocker = true)
class S3ObjectStorageAdapterIntegrationTest {
    private static final String ACCESS_KEY = "xianzhi-test-access";
    private static final String SECRET_KEY = "xianzhi-test-secret-password";
    private static final String BUCKET = "xianzhi-speech-test";
    private static final String KMS_KEY = "xianzhi:" + Base64.getEncoder().encodeToString(new byte[32]);

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withEnv("MINIO_KMS_SECRET_KEY", KMS_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @BeforeAll
    static void createBucket() {
        try (S3Client client = S3Client.builder().region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Test
    void storesStreamsAndDeletesObjectThroughMinio() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("s3");
        properties.setEndpoint(endpoint());
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setBucket(BUCKET);
        properties.setManageLifecycle(false);
        properties.setServerSideEncryption(true);
        var adapter = new S3ObjectStorageAdapter(properties);
        var file = new MockMultipartFile("audio", "sample.webm", "audio/webm",
                "streaming-audio".getBytes(StandardCharsets.UTF_8));

        String key = adapter.store(UUID.randomUUID(), UUID.randomUUID(), file);
        try (S3Client client = s3Client()) {
            var metadata = client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
            assertThat(metadata.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        }
        try (var input = adapter.open(key)) {
            assertThat(input.readAllBytes()).isEqualTo(file.getBytes());
        }
        adapter.delete(key);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.open(key));
    }

    private static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static S3Client s3Client() {
        return S3Client.builder().region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
