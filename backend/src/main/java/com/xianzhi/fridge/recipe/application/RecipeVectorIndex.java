package com.xianzhi.fridge.recipe.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.assistant.application.EmbeddingPort;
import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class RecipeVectorIndex {
    private static final Logger log = LoggerFactory.getLogger(RecipeVectorIndex.class);
    static final int VECTOR_SIZE = 64;
    private final AssistantProperties properties;
    private final ObjectMapper mapper;
    private final EmbeddingPort embedding;
    private final RecipeStore store;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Set<String> readyCollections = ConcurrentHashMap.newKeySet();

    @Autowired
    public RecipeVectorIndex(AssistantProperties properties, ObjectMapper mapper, EmbeddingPort embedding,
                             RecipeStore store) {
        this.properties = properties;
        this.mapper = mapper;
        this.embedding = embedding;
        this.store = store;
    }
    RecipeVectorIndex(AssistantProperties properties,ObjectMapper mapper){this(properties,mapper,new EmbeddingPort(){
        public boolean available(){return true;}public int dimensions(){return VECTOR_SIZE;}
        public float[] embed(String text){return RecipeVectorIndex.vector(text);}
    },null);}

    public boolean index(UUID recipeId, String title, String searchableText) {
        return indexInto(recipeId,title,searchableText,activeCollection());
    }

    public boolean indexInto(UUID recipeId, String title, String searchableText, String targetCollection) {
        if (!properties.isVectorEnabled() || !embedding.available()) return false;
        try {
            ensureCollection(targetCollection);
            ObjectNode point = mapper.createObjectNode();
            point.put("id", recipeId.toString());
            point.set("vector", vectorNode(searchableText));
            point.set("payload", mapper.createObjectNode().put("recipeId", recipeId.toString()).put("title", title));
            ObjectNode body = mapper.createObjectNode();
            body.putArray("points").add(point);
            send("PUT", "/collections/" + targetCollection + "/points?wait=true", body);
            return true;
        } catch (Exception exception) {
            readyCollections.remove(targetCollection);
            log.warn("Qdrant recipe indexing unavailable; MySQL search remains active: {}", exception.getMessage());
            return false;
        }
    }

    public List<UUID> search(String query, int limit) {
        if (!properties.isVectorEnabled() || !embedding.available() || query == null || query.isBlank()) return List.of();
        try {
            String activeCollection=activeCollection();
            ensureCollection(activeCollection);
            ObjectNode body = mapper.createObjectNode();
            body.set("query", vectorNode(query));
            body.put("limit", limit);
            body.put("with_payload", true);
            JsonNode response = send("POST", "/collections/" + activeCollection + "/points/query", body);
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

    private void ensureCollection(String collection) throws Exception {
        validateCollection(collection);
        if (readyCollections.contains(collection)) return;
        if (exists(collection)) { readyCollections.add(collection); return; }
        ObjectNode vectors = mapper.createObjectNode().put("size", embedding.dimensions()).put("distance", "Cosine");
        ObjectNode body = mapper.createObjectNode().set("vectors", vectors);
        send("PUT", "/collections/" + collection, body);
        readyCollections.add(collection);
    }

    private boolean exists(String collection) {
        try { send("GET", "/collections/" + collection, null); return true; }
        catch (Exception ignored) { return false; }
    }

    private JsonNode send(String method, String path, JsonNode body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getQdrantUrl().replaceAll("/$", "") + path))
                .timeout(Duration.ofSeconds(4)).header("Content-Type", "application/json");
        if(properties.getQdrantApiKey()!=null&&!properties.getQdrantApiKey().isBlank())builder.header("api-key",properties.getQdrantApiKey());
        HttpRequest.BodyPublisher publisher=body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8);
        HttpRequest request=builder.method(method,publisher).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Qdrant returned " + response.statusCode());
        return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
    }

    private ArrayNode vectorNode(String value) {
        ArrayNode array = mapper.createArrayNode();
        for (float coordinate : embedding.embed(value)) array.add(coordinate);
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

    private String activeCollection() {
        String value = properties.getQdrantCollection();
        if(store!=null)value=store.activeCollection(value);
        validateCollection(value);
        return value;
    }
    private static void validateCollection(String value){if(value==null||!value.matches("[A-Za-z0-9_-]{1,160}"))throw new IllegalStateException("Invalid Qdrant collection name");}
}
