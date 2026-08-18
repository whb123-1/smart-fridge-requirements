package com.xianzhi.fridge.recipe.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RecipeImportParserTest {
    private final RecipeImportParser parser = new RecipeImportParser(new ObjectMapper());

    @Test
    void parsesAndNormalizesARecipeDocumentDeterministically() {
        String payload = """
                {"recipes":[{"sourceRecipeId":"tofu-1","title":"香煎豆腐","summary":"简单家常菜",
                "cookMinutes":12,"servings":2,"nutrition":{"calories":360,"protein":24},
                "ingredients":[{"name":"豆腐","role":"PRIMARY","quantity":300,"unit":"g"},
                {"name":"盐","role":"SEASONING","quantity":2,"unit":"g"}],
                "steps":["豆腐切片","煎至两面金黄"]}]}
                """;

        var first = parser.parse(payload).getFirst();
        var second = parser.parse(payload).getFirst();
        assertThat(first.title()).isEqualTo("香煎豆腐");
        assertThat(first.components()).hasSize(2);
        assertThat(first.components().get(1).scalingRule()).isEqualTo("BOUNDED");
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint()).hasSize(64);
        assertThat(first.snapshotChecksum()).hasSize(64);
    }

    @Test
    void rejectsIncompleteRecipesBeforeAnyDatabaseWrite() {
        assertThatThrownBy(() -> parser.parse("[{\"title\":\"无步骤菜谱\",\"cookMinutes\":10,\"servings\":1,\"ingredients\":[]}]") )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ingredients");
    }
}
