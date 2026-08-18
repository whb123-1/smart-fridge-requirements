package com.xianzhi.fridge.recipe.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecipeVectorIndex {
    private static final Logger log = LoggerFactory.getLogger(RecipeVectorIndex.class);
    static final int VECTOR_SIZE = 64;
    private final AssistantProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private volatile boolean collectionReady;

    public RecipeVectorIndex(AssistantProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public boolean index(UUID recipeId, String title, String searchableText) {
        if (!properties.isVectorEnabled()) return false;
        try {
            ensureCollection();
            ObjectNode point = mapper.createObjectNode();
            point.put("id", recipeId.toString());
            point.set("vector", vectorNode(searchableText));
            point.set("payload", mapper.createObjectNode().put("recipeId", recipeId.toString()).put("title", title));
            ObjectNode body = mapper.createObjectNode();
            body.putArray("points").add(point);
            send("PUT", "/collections/" + collection() + "/points?wait=true", body);
            return true;
        } catch (Exception exception) {
            collectionReady = false;
            log.warn("Qdrant recipe indexing unavailable; MySQL search remains active: {}", exception.getMessage());
            return false;
        }
    }

    public List<UUID> search(String query, int limit) {
        if (!properties.isVectorEnabled() || query == null || query.isBlank()) return List.of();
        try {
            ensureCollection();
            ObjectNode body = mapper.createObjectNode();
            body.set("query", vectorNode(query));
            body.put("limit", limit);
            body.put("with_payload", true);
            JsonNode response = send("POST", "/collections/" + collection() + "/points/query", body);
            JsonNode points = response.path("result").path("points");
            List<UUID> ids = new ArrayList<>();
            if (points.isArray()) for (JsonNode point : points) {
                String value = point.path("payload").path("recipeId").asText(null);
                if (value != null) ids.add(UUID.fromString(value));
            }
            return ids;
        } catch (Exception exception) {
            log.warn("Qdrant recipe search unavailable; falling back to MySQL: {}", exception.getMessage());
            return List.of();
        }
    }

    private void ensureCollection() throws Exception {
        if (collectionReady) return;
        ObjectNode vectors = mapper.createObjectNode().put("size", VECTOR_SIZE).put("distance", "Cosine");
        ObjectNode body = mapper.createObjectNode().set("vectors", vectors);
        send("PUT", "/collections/" + collection(), body);
        collectionReady = true;
    }

    private JsonNode send(String method, String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getQdrantUrl().replaceAll("/$", "") + path))
                .timeout(Duration.ofSeconds(4)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Qdrant returned " + response.statusCode());
        return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
    }

    private ArrayNode vectorNode(String value) {
        ArrayNode array = mapper.createArrayNode();
        for (float coordinate : vector(value)) array.add(coordinate);
        return array;
    }

    static float[] vector(String value) {
        float[] vector = new float[VECTOR_SIZE];
        byte[] bytes = value.toLowerCase().getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < bytes.length; index++) {
            int slot = Math.floorMod((bytes[index] & 0xff) * 31 + index * 17, VECTOR_SIZE);
            vector[slot] += 1f;
        }
        double norm = 0;
        for (float coordinate : vector) norm += coordinate * coordinate;
        if (norm > 0) {
            float divisor = (float) Math.sqrt(norm);
            for (int index = 0; index < vector.length; index++) vector[index] /= divisor;
        }
        return vector;
    }

    private String collection() {
        String value = properties.getQdrantCollection();
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,120}")) throw new IllegalStateException("Invalid Qdrant collection name");
        return value;
    }
}
