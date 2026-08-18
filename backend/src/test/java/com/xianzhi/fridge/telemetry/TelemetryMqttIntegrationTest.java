package com.xianzhi.fridge.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Testcontainers(disabledWithoutDocker = true)
class TelemetryMqttIntegrationTest {
    private static final String SERVICE_USERNAME = "integration-service";
    private static final String SERVICE_PASSWORD = "integration-service-password";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("xianzhi_mqtt")
            .withUsername("xianzhi")
            .withPassword("test-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> EMQX = new GenericContainer<>(DockerImageName.parse("emqx/emqx:5.8.8"))
            .withExposedPorts(1883)
            .withEnv("EMQX_AUTHORIZATION__NO_MATCH", "allow")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379) + "/0");
        registry.add("app.security.jwt-signing-key",
                () -> "mqtt-integration-signing-key-with-at-least-32-bytes");
        registry.add("app.telemetry.enabled", () -> true);
        registry.add("app.telemetry.broker-url", TelemetryMqttIntegrationTest::brokerUrl);
        registry.add("app.telemetry.public-broker-url", TelemetryMqttIntegrationTest::brokerUrl);
        registry.add("app.telemetry.service-username", () -> SERVICE_USERNAME);
        registry.add("app.telemetry.service-password", () -> SERVICE_PASSWORD);
        registry.add("app.telemetry.internal-token", () -> "mqtt-integration-internal-token");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void qosOneTelemetryTravelsThroughEmqxAndDuplicateMessageIsNotStoredTwice() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String token = register("mqtt_" + suffix, "mqtt-" + suffix + "@example.com");
        String authorization = "Bearer " + token;

        MvcResult onboarding = mvc.perform(post("/api/v1/onboarding/initialize")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fridgeName":"MQTT integration fridge","zones":[
                                  {"kind":"CHILL","name":"Chill","temperatureSensorCount":1,"humiditySensorCount":0},
                                  {"kind":"FRESH","name":"Fresh","temperatureSensorCount":0,"humiditySensorCount":0},
                                  {"kind":"FREEZE","name":"Freeze","temperatureSensorCount":0,"humiditySensorCount":0}
                                ]}
                                """))
                .andExpect(status().isOk()).andReturn();
        JsonNode zones = data(onboarding).path("zones");
        String zoneId = zones.get(0).path("id").asText();

        MvcResult slots = mvc.perform(get("/api/v1/zones/{id}/sensors", zoneId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk()).andReturn();
        String sensorId = data(slots).get(0).path("id").asText();

        MvcResult created = mvc.perform(post("/api/v1/zones/{id}/devices", zoneId)
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MQTT test device\",\"type\":\"VIRTUAL\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode device = data(created);
        String deviceId = device.path("id").asText();
        JsonNode credential = device.path("credential");

        mvc.perform(post("/api/v1/devices/{id}/sensors", deviceId)
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + sensorId
                                + "\",\"name\":\"Temperature\",\"externalKey\":\"temp-1\"}"))
                .andExpect(status().isOk());

        UUID messageId = UUID.randomUUID();
        String payload = "{\"messageId\":\"" + messageId + "\",\"observedAt\":\"" + Instant.now()
                + "\",\"firmwareVersion\":\"integration-test\",\"readings\":[{\"sensorId\":\""
                + sensorId + "\",\"metric\":\"TEMPERATURE\",\"value\":4.0,\"unit\":\"C\",\"quality\":\"GOOD\"}]}";

        publish(credential, payload);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(count("sensor_reading", "sensor_id", sensorId)).isEqualTo(1));

        publish(credential, payload);
        Thread.sleep(750);
        assertThat(count("sensor_reading", "sensor_id", sensorId)).isEqualTo(1);
        assertThat(count("telemetry_message", "device_id", deviceId)).isEqualTo(1);
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                                + "\",\"password\":\"test-password\",\"displayName\":\"MQTT tester\"}"))
                .andExpect(status().isCreated()).andReturn();
        return data(result).path("accessToken").asText();
    }

    private void publish(JsonNode credential, String payload) throws Exception {
        MqttAsyncClient client = new MqttAsyncClient(brokerUrl(), credential.path("clientId").asText() + "-publisher");
        try {
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setUserName(credential.path("username").asText());
            options.setPassword(credential.path("password").asText().getBytes(StandardCharsets.UTF_8));
            client.connect(options).waitForCompletion();
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            message.setRetained(false);
            client.publish(credential.path("topic").asText(), message).waitForCompletion();
            client.disconnect().waitForCompletion();
        } finally {
            client.close();
        }
    }

    private int count(String table, String column, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=UUID_TO_BIN(?)",
                Integer.class, id);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String brokerUrl() {
        return "tcp://" + EMQX.getHost() + ":" + EMQX.getMappedPort(1883);
    }
}
