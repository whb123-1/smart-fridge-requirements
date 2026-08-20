package com.xianzhi.fridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantGenerationPort;
import com.xianzhi.fridge.identity.api.AdminUserContracts;
import com.xianzhi.fridge.identity.application.AdminUserService;
import com.xianzhi.fridge.identity.domain.UserRole;
import com.xianzhi.fridge.recipe.application.RecipeImportProcessor;
import com.xianzhi.fridge.shared.application.OutboxProcessor;
import com.xianzhi.fridge.shared.web.ApiException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(PhaseOneIntegrationTest.TestAiConfiguration.class)
class PhaseOneIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class TestAiConfiguration {
        @Bean
        @Primary
        AssistantGenerationPort testAssistantGenerationPort() {
            return (userMessage, page, contextJson) ->
                    new AssistantGenerationPort.GeneratedAnswer("测试模型回答", "test-chat", false, null);
        }
    }

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
        registry.add("app.security.admin-usernames", () -> "phase4_admin");
        registry.add("app.speech.fake-enabled", () -> "true");
        registry.add("app.speech.fake-transcript", () -> "番茄 2 个");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecipeImportProcessor recipeImports;
    @Autowired OutboxProcessor outbox;
    @Autowired AdminUserService adminUsers;

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
    void normalUserOnboardingAutomaticallyBindsVirtualSensorWithoutMqttCredentials() throws Exception {
        Session owner = register("sensor-owner-" + UUID.randomUUID() + "@example.com");
        MvcResult onboarding = mvc.perform(post("/api/v1/onboarding/initialize")
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fridgeName":"Sensor fridge","zones":[
                                  {"kind":"CHILL","name":"Chill","temperatureSensorCount":1,"humiditySensorCount":0},
                                  {"kind":"FRESH","name":"Fresh","temperatureSensorCount":0,"humiditySensorCount":0},
                                  {"kind":"FREEZE","name":"Freeze","temperatureSensorCount":0,"humiditySensorCount":0}
                                ]}
                                """))
                .andExpect(status().isOk()).andReturn();
        String zoneId = data(onboarding).path("zones").path(0).path("id").asText();
        MvcResult slots = mvc.perform(get("/api/v1/zones/{id}/sensors", zoneId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bindingStatus").value("BOUND"))
                .andExpect(jsonPath("$.data[0].deviceId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].source").doesNotExist())
                .andReturn();
        String slotId = data(slots).path(0).path("id").asText();
        String deviceId = data(slots).path(0).path("deviceId").asText();
        assertThat(jdbc.queryForObject("select device_type from device where id=UUID_TO_BIN(?)", String.class, deviceId))
                .isEqualTo("VIRTUAL");

        Session stranger = register("sensor-stranger-" + UUID.randomUUID() + "@example.com");
        mvc.perform(get("/api/v1/zones/{id}/sensors", zoneId)
                        .header("Authorization", bearer(stranger)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
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

    @Test
    void inventoryWritesAreIdempotentIsolatedAndSoftDeleteKeepsHistory() throws Exception {
        Session owner = register("inventory-" + UUID.randomUUID() + "@example.com");
        FridgeFixture fridge = initialize(owner);
        String key = UUID.randomUUID().toString();
        String createBody = objectMapper.writeValueAsString(Map.of(
                "fridgeId", fridge.id(), "name", "custom-" + UUID.randomUUID(), "category", "OTHER",
                "defaultUnit", "g", "batches", List.of(Map.of(
                        "zoneId", fridge.firstZoneId(), "storedAt", "2026-08-18T00:00:00Z",
                        "packageExpiresAt", "2030-08-18T00:00:00Z", "shelfLifeDays", 7,
                        "quantity", 5, "unit", "g"))));

        MvcResult created = mvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batches[0].assessment.estimationSource").value("REFERENCE_TARGET"))
                .andReturn();
        JsonNode createdData = data(created);
        String itemId = createdData.path("id").asText();
        String batchId = createdData.path("batches").path(0).path("id").asText();

        MvcResult initialHistory = mvc.perform(get("/api/v1/inventory/transactions")
                        .header("Authorization", bearer(owner)).param("fridgeId", fridge.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andReturn();
        String transactionId = data(initialHistory).path(0).path("id").asText();
        mvc.perform(delete("/api/v1/inventory/transactions/{id}", transactionId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/inventory/transactions")
                        .header("Authorization", bearer(owner)).param("fridgeId", fridge.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(itemId));
        mvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody.replace("\"quantity\":5", "\"quantity\":6")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mvc.perform(patch("/api/v1/inventory/batches/{id}", batchId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneId\":\"" + fridge.secondZoneId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageExpiresAt").doesNotExist());
        mvc.perform(patch("/api/v1/inventory/batches/{id}", batchId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"packageExpiresAt\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.assessment.estimationSource").value("REFERENCE_TARGET"));

        Session intruder = register("inventory-other-" + UUID.randomUUID() + "@example.com");
        mvc.perform(get("/api/v1/inventory/items").header("Authorization", bearer(intruder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(post("/api/v1/inventory/batches/{id}/transactions", batchId)
                        .header("Authorization", bearer(intruder)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONSUME\",\"quantity\":1,\"unit\":\"g\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/inventory/items/{id}", itemId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_HAS_ACTIVE_BATCHES"));
        mvc.perform(post("/api/v1/inventory/batches/{id}/transactions", batchId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUST\",\"quantity\":0,\"unit\":\"g\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/inventory/items/{id}", itemId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/inventory/items").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/inventory/batches/{id}/assessments", batchId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_transaction WHERE batch_id = UUID_TO_BIN(?)",
                Integer.class, batchId)).isEqualTo(2);
    }

    @Test
    void expiryUsesGlobalEstimatorForCataloguedAndCustomFood() throws Exception {
        Session account = register("expiry-" + UUID.randomUUID() + "@example.com");
        FridgeFixture fridge = initialize(account);
        String knownBody = objectMapper.writeValueAsString(Map.of(
                "fridgeId", fridge.id(), "name", "\u9e21\u80f8\u8089", "defaultUnit", "g",
                "batches", List.of(Map.of("zoneId", fridge.firstZoneId(), "quantity", 300, "unit", "g"))));
        mvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(knownBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batches[0].assessment.estimationSource").value("CATALOG_PROFILE"))
                .andExpect(jsonPath("$.data.batches[0].assessment.estimatedExpiryAt").exists());

        String customBody = objectMapper.writeValueAsString(Map.of(
                "fridgeId", fridge.id(), "name", "uncatalogued-" + UUID.randomUUID(), "category", "VEGETABLE",
                "defaultUnit", "g", "batches", List.of(Map.of(
                        "zoneId", fridge.firstZoneId(), "quantity", 1, "unit", "g"))));
        mvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(customBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batches[0].assessment.estimationSource").value("CATALOG_PROFILE"))
                .andExpect(jsonPath("$.data.batches[0].assessment.estimatedExpiryAt").exists());

        MvcResult suggestion = mvc.perform(get("/api/v1/catalog/suggestions")
                        .header("Authorization", bearer(account)).param("query", "\u9e21\u86cb"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("\u9e21\u86cb"))
                .andReturn();
        String catalogId = data(suggestion).path(0).path("id").asText();
        mvc.perform(get("/api/v1/catalog/weight-estimates")
                        .header("Authorization", bearer(account)).param("catalogId", catalogId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].referenceGrams").value(50.0));
    }

    @Test
    void concurrentConsumptionAllowsOnlyOneRequest() throws Exception {
        Session account = register("concurrency-" + UUID.randomUUID() + "@example.com");
        FridgeFixture fridge = initialize(account);
        String body = objectMapper.writeValueAsString(Map.of(
                "fridgeId", fridge.id(), "name", "concurrent-" + UUID.randomUUID(), "category", "OTHER",
                "defaultUnit", "g", "batches", List.of(Map.of("quantity", 5, "unit", "g"))));
        MvcResult created = mvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String batchId = data(created).path("batches").path(0).path("id").asText();
        Callable<MvcResult> consume = () -> mvc.perform(post("/api/v1/inventory/batches/{id}/transactions", batchId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONSUME\",\"quantity\":4,\"unit\":\"g\"}"))
                .andReturn();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<MvcResult>> futures = executor.invokeAll(List.of(consume, consume));
            assertThat(futures).extracting(future -> future.get().getResponse().getStatus())
                    .containsExactlyInAnyOrder(200, 409);
        }
        mvc.perform(get("/api/v1/inventory/items").header("Authorization", bearer(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batches[0].remainingQuantity").value(1.0));
    }

    @Test
    void shoppingStoreIsAtomicReplayableAndCannotBeBypassedByPatch() throws Exception {
        Session account = register("shopping-" + UUID.randomUUID() + "@example.com");
        FridgeFixture fridge = initialize(account);
        MvcResult listResult = mvc.perform(post("/api/v1/shopping-lists")
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fridgeId\":\"" + fridge.id() + "\",\"name\":\"Primary list\"}"))
                .andExpect(status().isOk()).andReturn();
        String listId = data(listResult).path("id").asText();
        mvc.perform(post("/api/v1/shopping-lists/{id}/items", listId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"invalid pair\",\"quantity\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String itemId = createShoppingItem(account, listId, "Store me");
        mvc.perform(patch("/api/v1/shopping-items/{id}", itemId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"STORED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHOPPING_ITEM_STORE_REQUIRED"));

        String storeKey = UUID.randomUUID().toString();
        String storeBody = "{\"fridgeId\":\"" + fridge.id() + "\",\"zoneId\":\"" + fridge.firstZoneId()
                + "\",\"quantity\":2,\"unit\":\"box\",\"shelfLifeDays\":7}";
        mvc.perform(post("/api/v1/shopping-items/{id}/store", itemId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", storeKey)
                        .contentType(MediaType.APPLICATION_JSON).content(storeBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("STORED"));
        mvc.perform(post("/api/v1/shopping-items/{id}/store", itemId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", storeKey)
                        .contentType(MediaType.APPLICATION_JSON).content(storeBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("STORED"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE display_name = 'Store me'", Integer.class)).isEqualTo(1);

        String rollbackItemId = createShoppingItem(account, listId, "Rollback me");
        mvc.perform(post("/api/v1/shopping-items/{id}/store", rollbackItemId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fridgeId\":\"" + fridge.id() + "\",\"zoneId\":\"" + UUID.randomUUID()
                                + "\",\"quantity\":1,\"unit\":\"box\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/shopping-lists").header("Authorization", bearer(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].items[?(@.id == '" + rollbackItemId + "')].status").value("PENDING"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE display_name = 'Rollback me'", Integer.class)).isZero();
    }

    @Test
    void voiceDraftRequiresConfirmationAndReplaysInventoryCreation() throws Exception {
        Session account = register("voice-" + UUID.randomUUID() + "@example.com");
        FridgeFixture fridge = initialize(account);
        MockMultipartFile audio = new MockMultipartFile("audio", "sample.wav", "audio/wav", new byte[]{1, 2, 3, 4});
        MvcResult uploaded = mvc.perform(multipart("/api/v1/inventory/voice-drafts")
                        .file(audio).param("fridgeId", fridge.id()).header("Authorization", bearer(account)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("UPLOADED")).andReturn();
        String draftId = data(uploaded).path("id").asText();
        outbox.processBatch();
        mvc.perform(get("/api/v1/inventory/voice-drafts/{id}", draftId)
                        .header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.draft.name").value("番茄"));

        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of("inventory", Map.of(
                "fridgeId", fridge.id(), "name", "番茄", "category", "VEGETABLE", "defaultUnit", "piece",
                "batches", List.of(Map.of("zoneId", fridge.firstZoneId(), "quantity", 2, "unit", "piece")))));
        MvcResult confirmed = mvc.perform(post("/api/v1/inventory/voice-drafts/{id}/confirm", draftId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String itemId = data(confirmed).path("id").asText();
        mvc.perform(post("/api/v1/inventory/voice-drafts/{id}/confirm", draftId)
                        .header("Authorization", bearer(account)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(itemId));
        assertThat(jdbc.queryForObject("select count(*) from inventory_item where id=UUID_TO_BIN(?)", Integer.class, itemId)).isEqualTo(1);
    }

    @Test
    void phaseFourRecipesMealsAssistantAndAdminImportArePersistentAndIsolated() throws Exception {
        Session owner = register("phase4-" + UUID.randomUUID() + "@example.com");
        MvcResult catalog = mvc.perform(get("/api/v1/recipes").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(catalog).size()).isGreaterThanOrEqualTo(25);
        Set<String> catalogImages = new HashSet<>();
        for (JsonNode recipe : data(catalog)) {
            assertThat(recipe.path("total").path("calories").isNumber())
                    .as("recipe %s must expose an AI catalog calorie estimate", recipe.path("name").asText())
                    .isTrue();
            String imageUrl = recipe.path("imageUrl").asText();
            if (!imageUrl.isBlank()) catalogImages.add(imageUrl);
        }
        assertThat(catalogImages).hasSizeGreaterThanOrEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from recipe where review_status='APPROVED'", Integer.class))
                .isGreaterThanOrEqualTo(25);
        assertThat(jdbc.queryForObject("select count(*) from (select r.id from recipe r left join recipe_component c on c.recipe_id=r.id left join recipe_step s on s.recipe_id=r.id left join recipe_search_index_state i on i.recipe_id=r.id where r.review_status='APPROVED' group by r.id,i.mysql_indexed_at having count(distinct c.id)=0 or count(distinct s.id)=0 or i.mysql_indexed_at is null) invalid_recipes", Integer.class))
                .isZero();
        mvc.perform(put("/api/v1/me/preferences").header("Authorization", bearer(owner))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tastes\":[],\"cuisines\":[],\"allergies\":[\"鸡蛋\"],\"dislikes\":[],\"calorieTarget\":1800}"))
                .andExpect(status().isOk());
        MvcResult filtered = mvc.perform(get("/api/v1/recipes").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode recipe : data(filtered)) assertThat(recipe.path("name").asText()).isNotEqualTo("番茄炒蛋");

        String mealKey = UUID.randomUUID().toString();
        String meal = "{\"mealAt\":\"2026-08-18T08:00:00Z\",\"mealType\":\"BREAKFAST\",\"name\":\"燕麦粥\",\"servings\":1,\"calories\":320,\"protein\":12,\"estimated\":true,\"nutritionSource\":\"RULE_ESTIMATE\"}";
        MvcResult createdMeal = mvc.perform(post("/api/v1/meals").header("Authorization", bearer(owner))
                        .header("Idempotency-Key", mealKey).contentType(MediaType.APPLICATION_JSON).content(meal))
                .andExpect(status().isOk()).andReturn();
        String mealId = data(createdMeal).path("id").asText();
        mvc.perform(post("/api/v1/meals").header("Authorization", bearer(owner))
                        .header("Idempotency-Key", mealKey).contentType(MediaType.APPLICATION_JSON).content(meal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(mealId));
        Session other = register("phase4-other-" + UUID.randomUUID() + "@example.com");
        mvc.perform(get("/api/v1/meals").header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        MvcResult conversation = mvc.perform(post("/api/v1/assistant/conversations")
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"测试会话\"}"))
                .andExpect(status().isOk()).andReturn();
        String conversationId = data(conversation).path("id").asText();
        String messageKey = UUID.randomUUID().toString();
        String message = "{\"content\":\"今天吃什么\",\"page\":\"home\",\"selection\":{\"email\":\"must-not-enter-context@example.com\"}}";
        MvcResult answer = mvc.perform(post("/api/v1/assistant/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", messageKey)
                        .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fallback").value(false)).andReturn();
        String messageId = data(answer).path("message").path("id").asText();
        mvc.perform(post("/api/v1/assistant/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", messageKey)
                        .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message.id").value(messageId));
        assertThat(jdbc.queryForObject("select count(*) from ai_context_snapshot where context_json like '%must-not-enter-context%'", Integer.class)).isZero();
        String proposalId = data(answer).path("actionProposals").path(0).path("id").asText();
        mvc.perform(put("/api/v1/me/preferences").header("Authorization", bearer(owner))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tastes\":[],\"cuisines\":[],\"allergies\":[\"花生\"],\"dislikes\":[],\"calorieTarget\":1800}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/assistant/action-proposals/{id}/confirm", proposalId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTEXT_STALE"));

        Session admin = register("phase4-admin@example.com", "phase4_admin");
        MvcResult sources = mvc.perform(get("/api/v1/admin/recipe-sources").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn();
        String sourceId = data(sources).path(0).path("id").asText();
        Map<String,Object> importedRecipe = new HashMap<>();
        importedRecipe.put("sourceRecipeId", "integration-tofu");
        importedRecipe.put("title", "香煎豆腐");
        importedRecipe.put("summary", "集成测试菜谱");
        importedRecipe.put("cookMinutes", 12);
        importedRecipe.put("servings", 2);
        importedRecipe.put("ingredients", List.of(Map.of("name", "豆腐", "role", "PRIMARY", "quantity", 300, "unit", "g")));
        importedRecipe.put("steps", List.of("豆腐切片", "煎至两面金黄"));
        importedRecipe.put("nutrition", Map.of("calories", 360, "protein", 24));
        importedRecipe.put("imageUrl", "https://www.themealdb.com/images/media/meals/integration.jpg");
        importedRecipe.put("imageSourceUrl", "https://www.themealdb.com/meal/integration-tofu");
        importedRecipe.put("imageAttribution", "Image: TheMealDB");
        String importBody = objectMapper.writeValueAsString(Map.of("sourceId", sourceId, "payload", Map.of("recipes", List.of(importedRecipe))));
        String importKey = UUID.randomUUID().toString();
        MvcResult queued = mvc.perform(post("/api/v1/admin/recipe-import-jobs")
                        .header("Authorization", bearer(admin)).header("Idempotency-Key", importKey)
                        .contentType(MediaType.APPLICATION_JSON).content(importBody))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("QUEUED")).andReturn();
        String jobId = data(queued).path("id").asText();
        recipeImports.processBatch();
        mvc.perform(get("/api/v1/admin/recipe-import-jobs/{id}", jobId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.importedCount").value(1));
        mvc.perform(get("/api/v1/recipes").param("query", "香煎豆腐").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("香煎豆腐"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://www.themealdb.com/images/media/meals/integration.jpg"))
                .andExpect(jsonPath("$.data[0].imageAttribution").value("Image: TheMealDB"));
        MvcResult ingredientSearch = mvc.perform(get("/api/v1/recipes").param("query", "豆腐")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(ingredientSearch).findValuesAsText("name")).contains("香煎豆腐");
        MvcResult ingredientGenerate = mvc.perform(post("/api/v1/recipes/generate")
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"豆腐\",\"inventory\":[],\"count\":3}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(ingredientGenerate).path("recipes").findValuesAsText("name")).contains("香煎豆腐");
        assertThat(data(ingredientGenerate).path("fallback").asBoolean()).isTrue();
        assertThat(data(ingredientGenerate).path("model").asText()).isEqualTo("rules-v2");
        MvcResult naturalLanguageGenerate = mvc.perform(post("/api/v1/recipes/generate")
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"晚餐想吃清淡的\",\"inventory\":[],\"count\":3}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(naturalLanguageGenerate).path("recipes").size()).isEqualTo(3);
        mvc.perform(get("/api/v1/admin/recipe-sources").header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorControlsUserLifecycleAndRevocationIsImmediate() throws Exception {
        Session administrator = register("lifecycle-admin-" + UUID.randomUUID() + "@example.com");
        jdbc.update("update app_user set role='ADMIN' where username=?", administrator.username());
        Session target = register("lifecycle-user-" + UUID.randomUUID() + "@example.com");

        MvcResult users = mvc.perform(get("/api/v1/admin/users")
                        .param("query", target.username()).header("Authorization", bearer(administrator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andReturn();
        String targetId = data(users).path("items").path(0).path("id").asText();

        mvc.perform(patch("/api/v1/admin/users/{id}/status", targetId)
                        .header("Authorization", bearer(administrator)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(target)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(target.refreshToken())))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/admin/users/{id}/status", targetId)
                        .header("Authorization", bearer(administrator)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        MvcResult relogin = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + target.username() + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        String accessBeforeReset = data(relogin).path("accessToken").asText();

        MvcResult reset = mvc.perform(post("/api/v1/admin/users/{id}/password-reset", targetId)
                        .header("Authorization", bearer(administrator)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.temporaryPassword").isString())
                .andReturn();
        String temporaryPassword = data(reset).path("temporaryPassword").asText();
        assertThat(temporaryPassword).hasSize(24);
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessBeforeReset))
                .andExpect(status().isUnauthorized());

        String temporaryLoginBody = objectMapper.writeValueAsString(Map.of(
                "identifier", target.username(), "password", temporaryPassword));
        MvcResult temporaryLogin = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(temporaryLoginBody)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.passwordChangeRequired").value(true)).andReturn();
        String temporaryAccess = data(temporaryLogin).path("accessToken").asText();
        mvc.perform(get("/api/v1/fridges").header("Authorization", "Bearer " + temporaryAccess))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(patch("/api/v1/me/password").header("Authorization", "Bearer " + temporaryAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", temporaryPassword, "newPassword", "new-test-password"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + target.username() + "\",\"password\":\"new-test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.passwordChangeRequired").value(false));

        mvc.perform(delete("/api/v1/admin/users/{id}", targetId)
                        .header("Authorization", bearer(administrator)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/users/{id}/restore", targetId)
                        .header("Authorization", bearer(administrator)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/users/{id}", data(mvc.perform(get("/api/v1/admin/users")
                                .param("query", administrator.username()).header("Authorization", bearer(administrator)))
                        .andReturn()).path("items").path(0).path("id").asText())
                        .header("Authorization", bearer(administrator)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_SELF_ACTION_FORBIDDEN"));
    }

    @Test
    void administratorAuthorizationMatrixAuditAndLastAdminLockAreEnforced() throws Exception {
        Session first = register("matrix-admin-a-" + UUID.randomUUID() + "@example.com");
        Session second = register("matrix-admin-b-" + UUID.randomUUID() + "@example.com");
        Session target = register("matrix-user-" + UUID.randomUUID() + "@example.com");
        // This shared integration database retains users created by earlier test methods.
        // Isolate the invariant under test so only this pair can satisfy the last-admin guard.
        jdbc.update("update app_user set role='USER' where role='ADMIN'");
        jdbc.update("update app_user set role='ADMIN' where username in (?,?)", first.username(), second.username());
        UUID firstId = userId(first.username());
        UUID secondId = userId(second.username());
        UUID targetId = userId(target.username());

        mvc.perform(get("/api/v1/admin/users").param("status", "not-a-status")
                        .header("Authorization", bearer(first)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/admin/users/{id}", targetId).header("Authorization", bearer(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(target.username()));

        mvc.perform(patch("/api/v1/admin/users/{id}/role", targetId)
                        .header("Authorization", bearer(first)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(target)))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/{id}/role", targetId)
                        .header("Authorization", bearer(first)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("USER"));
        mvc.perform(post("/api/v1/admin/users/{id}/sessions/revoke", targetId)
                        .header("Authorization", bearer(first)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.action").value("SESSIONS_REVOKED"));
        mvc.perform(post("/api/v1/admin/users/{id}/restore", targetId)
                        .header("Authorization", bearer(first)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_NOT_DELETED"));
        mvc.perform(get("/api/v1/admin/users/{id}/audit-logs", targetId)
                        .header("Authorization", bearer(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> demoteSecond = executor.submit(() -> demoteAfter(start, firstId, secondId));
            Future<String> demoteFirst = executor.submit(() -> demoteAfter(start, secondId, firstId));
            start.countDown();
            assertThat(List.of(demoteSecond.get(), demoteFirst.get()))
                    .containsExactlyInAnyOrder("ROLE_CHANGED", "LAST_ADMIN_REQUIRED");
        }
        Long activeAdmins = jdbc.queryForObject("select count(*) from app_user where role='ADMIN' and status='ACTIVE' and deleted_at is null", Long.class);
        assertThat(activeAdmins).isEqualTo(1L);
    }

    @Test
    void zoneSettingsAndMealDeletionArePersistedAndUserIsolated() throws Exception {
        Session owner = register("persistence-owner-" + UUID.randomUUID() + "@example.com");
        Session stranger = register("persistence-stranger-" + UUID.randomUUID() + "@example.com");
        FridgeFixture fridge = initialize(owner);

        String zoneKey = UUID.randomUUID().toString();
        mvc.perform(patch("/api/v1/zones/{id}", fridge.firstZoneId())
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", zoneKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main chill\",\"targetTemperatureC\":3.5,\"targetHumidityPct\":72}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Main chill"))
                .andExpect(jsonPath("$.data.targetTemperatureC").value(3.5));
        Map<String, Object> savedZone = jdbc.queryForMap("select name,target_temperature_c,target_humidity_pct from fridge_zone where id=UUID_TO_BIN(?)", fridge.firstZoneId());
        assertThat(savedZone.get("name")).isEqualTo("Main chill");
        assertThat(savedZone.get("target_temperature_c").toString()).isEqualTo("3.50");
        assertThat(savedZone.get("target_humidity_pct").toString()).isEqualTo("72.00");

        mvc.perform(patch("/api/v1/zones/{id}", fridge.firstZoneId())
                        .header("Authorization", bearer(stranger)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stolen\",\"targetTemperatureC\":4,\"targetHumidityPct\":70}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));

        mvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fridgeId\":\"" + fridge.id() + "\",\"name\":\"Persisted apple\",\"category\":\"FRUIT\",\"defaultUnit\":\"piece\",\"batches\":[{\"zoneId\":\"" + fridge.firstZoneId() + "\",\"quantity\":3,\"unit\":\"piece\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/inventory/transactions").param("fridgeId", fridge.id()).param("limit", "5")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemName").value("Persisted apple"))
                .andExpect(jsonPath("$.data[0].type").value("IN"))
                .andExpect(jsonPath("$.data[0].afterQuantity").value(3));
        mvc.perform(get("/api/v1/inventory/transactions").param("fridgeId", fridge.id())
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIDGE_NOT_FOUND"));

        MvcResult createdMeal = mvc.perform(post("/api/v1/meals")
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealAt\":\"2026-08-19T06:30:00Z\",\"mealType\":\"早餐\",\"name\":\"鸡蛋\",\"servings\":1,\"calories\":120,\"estimated\":false,\"nutritionSource\":\"TEST\"}"))
                .andExpect(status().isOk()).andReturn();
        String mealId = data(createdMeal).path("id").asText();
        mvc.perform(delete("/api/v1/meals/{id}", mealId)
                        .header("Authorization", bearer(stranger)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEAL_NOT_FOUND"));
        String deleteKey = UUID.randomUUID().toString();
        mvc.perform(delete("/api/v1/meals/{id}", mealId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", deleteKey))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/meals/{id}", mealId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", deleteKey))
                .andExpect(status().isOk());
        Long remaining = jdbc.queryForObject("select count(*) from meal_record where id=UUID_TO_BIN(?)", Long.class, mealId);
        assertThat(remaining).isZero();
    }

    @Test
    void plannedRecipesArePersistedPerUserAndFridge() throws Exception {
        Session owner = register("recipe-plan-owner-" + UUID.randomUUID() + "@example.com");
        Session stranger = register("recipe-plan-stranger-" + UUID.randomUUID() + "@example.com");
        FridgeFixture fridge = initialize(owner);
        MvcResult recipeResult = mvc.perform(get("/api/v1/recipes").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn();
        String recipeId = data(recipeResult).path(0).path("id").asText();

        MvcResult created = mvc.perform(post("/api/v1/fridges/{fridgeId}/recipe-plans", fridge.id())
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":\"" + recipeId + "\",\"servings\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipeId").value(recipeId))
                .andExpect(jsonPath("$.data.servings").value(2))
                .andReturn();
        String planId = data(created).path("id").asText();

        mvc.perform(get("/api/v1/fridges/{fridgeId}/recipe-plans", fridge.id())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(planId))
                .andExpect(jsonPath("$.data[0].recipe.ingredients").isArray());
        mvc.perform(post("/api/v1/fridges/{fridgeId}/recipe-plans", fridge.id())
                        .header("Authorization", bearer(stranger)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":\"" + recipeId + "\",\"servings\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIDGE_NOT_FOUND"));
        mvc.perform(patch("/api/v1/recipe-plans/{id}", planId)
                        .header("Authorization", bearer(stranger)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\":3}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECIPE_PLAN_NOT_FOUND"));
        mvc.perform(patch("/api/v1/recipe-plans/{id}", planId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.servings").value(3));
        mvc.perform(delete("/api/v1/recipe-plans/{id}", planId)
                        .header("Authorization", bearer(owner)).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/fridges/{fridgeId}/recipe-plans", fridge.id())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    private String demoteAfter(CountDownLatch start, UUID actor, UUID target) throws Exception {
        start.await();
        try {
            return adminUsers.role(actor, target, UUID.randomUUID().toString(),
                    new AdminUserContracts.RoleRequest(UserRole.USER)).action();
        } catch (ApiException exception) {
            return exception.getCode();
        }
    }

    private UUID userId(String username) {
        return UUID.fromString(jdbc.queryForObject("select BIN_TO_UUID(id) from app_user where username=?", String.class, username));
    }

    private Session register(String email) throws Exception {
        return register(email, newUsername());
    }

    private Session register(String email, String username) throws Exception {
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

    private FridgeFixture initialize(Session session) throws Exception {
        String body = """
                {"fridgeName":"Phase two fridge","zones":[
                  {"kind":"CHILL","name":"Chill","temperatureSensorCount":0,"humiditySensorCount":0},
                  {"kind":"FRESH","name":"Fresh","temperatureSensorCount":0,"humiditySensorCount":0},
                  {"kind":"FREEZE","name":"Freeze","temperatureSensorCount":0,"humiditySensorCount":0}
                ]}
                """;
        MvcResult result = mvc.perform(post("/api/v1/onboarding/initialize")
                        .header("Authorization", bearer(session)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode value = data(result);
        return new FridgeFixture(value.path("id").asText(), value.path("zones").path(0).path("id").asText(),
                value.path("zones").path(1).path("id").asText());
    }

    private String createShoppingItem(Session session, String listId, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/shopping-lists/{id}/items", listId)
                        .header("Authorization", bearer(session)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"category\":\"DAIRY\",\"quantity\":2,\"unit\":\"box\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(result).path("id").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String bearer(Session session) { return "Bearer " + session.accessToken(); }

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
    private record FridgeFixture(String id, String firstZoneId, String secondZoneId) { }
}
