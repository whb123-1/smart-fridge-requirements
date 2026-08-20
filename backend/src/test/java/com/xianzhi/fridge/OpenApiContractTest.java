package com.xianzhi.fridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void contractIsValidYamlAndCoversImplementedEndpoints() throws Exception {
        Object document = new Yaml().load(Files.readString(Path.of("openapi.yaml")));
        assertThat(document).isInstanceOf(Map.class);

        Map<String, Object> root = (Map<String, Object>) document;
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        assertThat(paths.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
                "/me", "/me/password", "/onboarding", "/onboarding/initialize", "/fridges",
                "/inventory/items", "/inventory/transactions", "/inventory/transactions/{id}", "/inventory/items/{id}", "/inventory/items/{id}/batches",
                "/inventory/batches/{id}", "/inventory/batches/{id}/transactions", "/expiry",
                "/inventory/batches/{id}/assessments", "/catalog/suggestions", "/catalog/weight-estimates",
                "/shopping-lists", "/shopping-lists/{id}/items", "/shopping-items/{id}", "/shopping-items/{id}/store",
                "/zones/{id}", "/zones/{id}/devices", "/zones/{id}/sensors",
                "/devices/{deviceId}/sensors/{sensorId}", "/zones/{id}/readings", "/fridges/{id}/environment",
                "/notifications", "/notifications/{id}", "/me/preferences", "/me/notification-preferences",
                "/inventory/voice-drafts", "/inventory/voice-drafts/{id}", "/inventory/voice-drafts/{id}/confirm",
                "/recipes", "/recipes/{id}", "/recipes/generate", "/recipe-synthesis/match",
                "/recipes/{id}/scale", "/recipes/{id}/bookmark", "/recipes/{id}/cook",
                "/fridges/{fridgeId}/recipe-plans", "/recipe-plans/{id}",
                "/admin/recipe-sources", "/admin/recipe-sources/{id}",
                "/admin/recipe-import-jobs", "/admin/recipe-import-jobs/{id}", "/admin/recipe-import-jobs/{id}/retry",
                "/admin/search-index", "/admin/search-index/rebuild", "/admin/users", "/admin/users/{id}",
                "/admin/users/{id}/audit-logs", "/admin/users/{id}/status", "/admin/users/{id}/role",
                "/admin/users/{id}/sessions/revoke", "/admin/users/{id}/password-reset", "/admin/users/{id}/restore",
                "/meals/estimate-nutrition", "/meals", "/meals/{id}", "/analytics/consumption", "/analytics/diet",
                "/assistant/briefing", "/assistant/conversations",
                "/assistant/conversations/{conversationId}/messages",
                "/assistant/action-proposals/{id}/confirm", "/assistant/action-proposals/{id}/dismiss"));

        Map<String, Object> components = (Map<String, Object>) root.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> register = (Map<String, Object>) schemas.get("RegisterRequest");
        Map<String, Object> login = (Map<String, Object>) schemas.get("LoginRequest");
        Map<String, Object> user = (Map<String, Object>) schemas.get("User");
        assertThat((Iterable<String>) register.get("required")).contains("username");
        assertThat((Iterable<String>) login.get("required")).contains("identifier").doesNotContain("email");
        assertThat(((Map<String, Object>) user.get("properties"))).containsKeys("username", "role", "status", "passwordChangeRequired");
    }
}
