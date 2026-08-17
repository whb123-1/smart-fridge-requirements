package com.xianzhi.fridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PhaseOneIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("xianzhi")
            .withUsername("xianzhi")
            .withPassword("test-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379) + "/0");
        registry.add("app.security.jwt-signing-key",
                () -> "integration-test-signing-key-with-at-least-32-bytes");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void onboardingIsIdempotentAndDataIsIsolatedByUser() throws Exception {
        Session first = register("first-" + UUID.randomUUID() + "@example.com");
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {"fridgeName":"Primary fridge","zones":[
                  {"kind":"CHILL","name":"Chill","temperatureSensorCount":1,"humiditySensorCount":1},
                  {"kind":"FRESH","name":"Fresh","temperatureSensorCount":0,"humiditySensorCount":0},
                  {"kind":"FREEZE","name":"Freeze","temperatureSensorCount":1,"humiditySensorCount":0}
                ]}
                """;

        MvcResult created = mvc.perform(post("/api/v1/onboarding/initialize")
                        .header("Authorization", "Bearer " + first.accessToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.zones.length()").value(3))
                .andExpect(jsonPath("$.data.zones[1].sensorBindingStatus").value("NOT_CONNECTED"))
                .andReturn();

        String fridgeId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();
        mvc.perform(post("/api/v1/onboarding/initialize")
                        .header("Authorization", "Bearer " + first.accessToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fridgeId));

        Session second = register("second-" + UUID.randomUUID() + "@example.com");
        mvc.perform(get("/api/v1/fridges")
                        .header("Authorization", "Bearer " + second.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void refreshReplayRevokesTheWholeTokenFamily() throws Exception {
        Session original = register("refresh-" + UUID.randomUUID() + "@example.com");
        MvcResult rotatedResult = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(original.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(original.username()))
                .andReturn();
        String rotated = cookieValue(rotatedResult);
        assertThat(rotated).isNotEqualTo(original.refreshToken());

        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(original.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(rotated)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSupportsUsernameAndLegacyEmailAndProfileCanRenameUsername() throws Exception {
        Session account = register("identity-" + UUID.randomUUID() + "@example.com");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + account.username().toUpperCase()
                                + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(account.username()));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email()
                                + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(account.username()));

        String renamed = newUsername();
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + renamed
                                + "\",\"displayName\":\"Renamed user\",\"timezone\":\"Asia/Shanghai\","
                                + "\"temperatureUnit\":\"C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(renamed));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + renamed + "\",\"email\":\"duplicate-"
                                + UUID.randomUUID()
                                + "@example.com\",\"password\":\"test-password\",\"displayName\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_REGISTERED"));
    }

    @Test
    void registrationRejectsInvalidUsername() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Bad Name\",\"email\":\"invalid-" + UUID.randomUUID()
                                + "@example.com\",\"password\":\"test-password\",\"displayName\":\"Invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fields.username").exists());
    }

    private Session register(String email) throws Exception {
        String username = newUsername();
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                                + "\",\"password\":\"test-password\",\"displayName\":\"Test user\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(body.path("data").path("accessToken").asText(), cookieValue(result), username, email);
    }

    private static String newUsername() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private static String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).startsWith("xianzhi_refresh=");
        return header.substring("xianzhi_refresh=".length(), header.indexOf(';'));
    }

    private static Cookie refreshCookie(String value) {
        return new Cookie("xianzhi_refresh", value);
    }

    private record Session(String accessToken, String refreshToken, String username, String email) { }
}
