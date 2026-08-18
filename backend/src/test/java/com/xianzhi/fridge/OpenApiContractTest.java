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
                "/inventory/items", "/inventory/items/{id}", "/inventory/items/{id}/batches",
                "/inventory/batches/{id}", "/inventory/batches/{id}/transactions", "/expiry",
                "/inventory/batches/{id}/assessments", "/catalog/suggestions", "/catalog/weight-estimates",
                "/shopping-lists", "/shopping-lists/{id}/items", "/shopping-items/{id}", "/shopping-items/{id}/store"));

        Map<String, Object> components = (Map<String, Object>) root.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> register = (Map<String, Object>) schemas.get("RegisterRequest");
        Map<String, Object> login = (Map<String, Object>) schemas.get("LoginRequest");
        Map<String, Object> user = (Map<String, Object>) schemas.get("User");
        assertThat((Iterable<String>) register.get("required")).contains("username");
        assertThat((Iterable<String>) login.get("required")).contains("identifier").doesNotContain("email");
        assertThat(((Map<String, Object>) user.get("properties"))).containsKey("username");
    }
}
