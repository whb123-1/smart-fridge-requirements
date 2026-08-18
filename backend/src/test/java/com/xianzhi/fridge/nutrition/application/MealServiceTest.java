package com.xianzhi.fridge.nutrition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xianzhi.fridge.shared.web.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MealServiceTest {
    @Test
    void nutritionScaleHonorsStableUnitsWithoutGuessingConversions() {
        assertThat(MealService.nutritionScale(new BigDecimal("250"), "g")).isEqualByComparingTo("1");
        assertThat(MealService.nutritionScale(new BigDecimal("0.25"), "kg")).isEqualByComparingTo("1");
        assertThat(MealService.nutritionScale(new BigDecimal("2"), "serving")).isEqualByComparingTo("2");
        assertThatThrownBy(() -> MealService.nutritionScale(BigDecimal.ONE, "handful"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("UNIT_NOT_CONVERTIBLE");
    }
}
