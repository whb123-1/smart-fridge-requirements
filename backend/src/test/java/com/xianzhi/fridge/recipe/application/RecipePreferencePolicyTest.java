package com.xianzhi.fridge.recipe.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.xianzhi.fridge.identity.api.PreferenceContracts;
import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipePreferencePolicyTest {
    private final RecipePreferencePolicy policy = new RecipePreferencePolicy();

    @Test
    void rejectsAllergensAndCuisineMismatches() {
        RecipeStore.Row recipe = row("花生鸡丁", "川菜", "中辣", new BigDecimal("30"), new BigDecimal("20"));
        var ingredients = List.of(component("鸡肉"), component("花生"));
        assertThat(policy.allows(recipe, ingredients, preference(List.of("中辣"), List.of("川菜"), List.of("花生"), null))).isFalse();
        assertThat(policy.allows(recipe, ingredients, preference(List.of("中辣"), List.of("粤菜"), List.of(), null))).isFalse();
    }

    @Test
    void supportsNutritionBasedTastePreferences() {
        RecipeStore.Row light = row("清蒸鸡胸", "家常菜", "咸鲜", new BigDecimal("24"), new BigDecimal("50"));
        RecipeStore.Row oily = row("油炸鸡排", "家常菜", "香辣", new BigDecimal("60"), new BigDecimal("40"));
        var preference = preference(List.of("少油", "清淡"), List.of("家常菜"), List.of(), "减脂");
        assertThat(policy.allows(light, List.of(component("鸡胸肉")), preference)).isTrue();
        assertThat(policy.allows(oily, List.of(component("鸡肉")), preference)).isFalse();
    }

    private static PreferenceContracts.View preference(List<String> tastes,List<String> cuisines,List<String> allergies,String goal){
        return new PreferenceContracts.View(tastes,cuisines,allergies,List.of(),goal,1800,TemperatureUnit.C);
    }

    private static RecipeStore.Row row(String title,String cuisine,String taste,BigDecimal fat,BigDecimal carbs){
        return new RecipeStore.Row(UUID.randomUUID(),title,title+" 做法",cuisine,taste,"均衡",20,BigDecimal.valueOf(2),
                BigDecimal.valueOf(600),BigDecimal.valueOf(60),fat,carbs,"CATALOG","CURATED","1","",null,null,null);
    }

    private static RecipeContracts.Component component(String name){
        return new RecipeContracts.Component(UUID.randomUUID(),name,"PRIMARY",BigDecimal.ONE,"g","LINEAR",null,null);
    }
}
