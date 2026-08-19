package com.xianzhi.fridge.recipe.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.assistant.application.AssistantProperties;
import com.xianzhi.fridge.assistant.application.EmbeddingPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers(disabledWithoutDocker = true)
class QdrantRecipeVectorIndexIntegrationTest {
    private static final String API_KEY = "qdrant-integration-secret";

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>("qdrant/qdrant:v1.15.4")
            .withEnv("QDRANT__SERVICE__API_KEY", API_KEY)
            .withExposedPorts(6333)
            .waitingFor(Wait.forHttp("/healthz").forPort(6333));

    @Test
    void indexesAndQueriesVersionedCollection() {
        AssistantProperties properties = new AssistantProperties();
        properties.setVectorEnabled(true);
        properties.setQdrantUrl("http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
        properties.setQdrantApiKey(API_KEY);
        properties.setQdrantCollection("recipes_test_" + UUID.randomUUID().toString().replace("-", ""));
        EmbeddingPort embedding = new EmbeddingPort() {
            @Override public boolean available() { return true; }
            @Override public int dimensions() { return 1536; }
            @Override public float[] embed(String text) {
                float[] vector = new float[dimensions()];
                vector[Math.floorMod(text.hashCode(), vector.length)] = 1f;
                return vector;
            }
        };
        RecipeVectorIndex index = new RecipeVectorIndex(properties, new ObjectMapper(), embedding, null);
        UUID recipeId = UUID.randomUUID();

        assertThat(index.index(recipeId, "番茄炒蛋", "番茄 鸡蛋 家常菜")).isTrue();
        assertThat(index.search("番茄 鸡蛋 家常菜", 5)).contains(recipeId);
    }
}
