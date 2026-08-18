package com.xianzhi.fridge.recipe.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecipeVectorIndexTest {
    @Test
    void localFallbackVectorIsDeterministicAndNormalized() {
        float[] first = RecipeVectorIndex.vector("番茄炒蛋");
        float[] second = RecipeVectorIndex.vector("番茄炒蛋");
        double norm = 0;
        for (float value : first) norm += value * value;
        assertThat(first).containsExactly(second);
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }
}
