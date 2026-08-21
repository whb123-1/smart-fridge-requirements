package com.xianzhi.fridge.recipe.application;

import com.xianzhi.fridge.recipe.api.RecipeContracts;
import com.xianzhi.fridge.recipe.infrastructure.RecipeStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RecipePromptPolicy {
    private static final Map<String, List<String>> INGREDIENT_FAMILIES = new LinkedHashMap<>();
    private static final Map<String, List<String>> METHOD_FAMILIES = new LinkedHashMap<>();
    private static final Map<String, List<String>> ATTRIBUTE_FAMILIES = new LinkedHashMap<>();

    static {
        family(INGREDIENT_FAMILIES, "猪肘", "猪肘", "肘子", "蹄膀", "猪肘子");
        family(INGREDIENT_FAMILIES, "猪肉", "猪肉", "猪肉末", "肉末", "五花肉", "排骨", "里脊");
        family(INGREDIENT_FAMILIES, "牛肉", "牛肉", "牛肉片", "牛腩", "牛排", "和牛", "肥牛");
        family(INGREDIENT_FAMILIES, "羊肉", "羊肉", "羊排", "羊腿");
        family(INGREDIENT_FAMILIES, "鸡肉", "鸡肉", "鸡胸", "鸡胸肉", "鸡腿", "鸡腿肉", "鸡翅", "鸡丁");
        family(INGREDIENT_FAMILIES, "鸭肉", "鸭肉", "鸭胸", "鸭腿");
        family(INGREDIENT_FAMILIES, "鹅肉", "鹅", "鹅肉", "鹅腿", "鹅掌");
        family(INGREDIENT_FAMILIES, "鱼", "鱼", "鱼片", "鲈鱼", "鳕鱼", "三文鱼", "带鱼", "鲫鱼", "鲤鱼");
        family(INGREDIENT_FAMILIES, "虾", "虾", "虾仁", "大虾", "基围虾");
        family(INGREDIENT_FAMILIES, "蟹", "蟹", "螃蟹", "蟹肉");
        family(INGREDIENT_FAMILIES, "鸡蛋", "鸡蛋", "蛋液", "滑蛋", "炒蛋");
        family(INGREDIENT_FAMILIES, "豆腐", "豆腐", "北豆腐", "嫩豆腐", "内酯豆腐");
        family(INGREDIENT_FAMILIES, "番茄", "番茄", "西红柿");
        family(INGREDIENT_FAMILIES, "土豆", "土豆", "马铃薯");
        family(INGREDIENT_FAMILIES, "西兰花", "西兰花", "花椰菜");
        family(INGREDIENT_FAMILIES, "茄子", "茄子", "eggplant");
        family(INGREDIENT_FAMILIES, "香菇", "香菇", "蘑菇", "口蘑");
        family(INGREDIENT_FAMILIES, "菠菜", "菠菜");
        family(INGREDIENT_FAMILIES, "生菜", "生菜");
        family(INGREDIENT_FAMILIES, "胡萝卜", "胡萝卜", "红萝卜");
        family(INGREDIENT_FAMILIES, "玉米", "玉米", "甜玉米");
        family(INGREDIENT_FAMILIES, "南瓜", "南瓜");
        family(INGREDIENT_FAMILIES, "冬瓜", "冬瓜");
        family(INGREDIENT_FAMILIES, "面条", "面条", "意面");
        family(INGREDIENT_FAMILIES, "米饭", "米饭", "大米");
        family(INGREDIENT_FAMILIES, "香蕉", "香蕉");
        family(INGREDIENT_FAMILIES, "牛油果", "牛油果", "鳄梨");

        family(METHOD_FAMILIES, "焖", "焖", "红焖", "黄焖", "焖烧");
        family(METHOD_FAMILIES, "红烧", "红烧", "烧制");
        family(METHOD_FAMILIES, "清蒸", "清蒸", "蒸制", "蒸");
        family(METHOD_FAMILIES, "煎", "香煎", "煎制", "煎");
        family(METHOD_FAMILIES, "炒", "快炒", "爆炒", "小炒", "炒");
        family(METHOD_FAMILIES, "烤", "烤", "烘烤");
        family(METHOD_FAMILIES, "炖", "炖", "慢炖", "煲");
        family(METHOD_FAMILIES, "凉拌", "凉拌", "拌");

        family(ATTRIBUTE_FAMILIES, "高蛋白", "高蛋白", "增肌");
        family(ATTRIBUTE_FAMILIES, "低脂", "低脂", "少油", "减脂");
        family(ATTRIBUTE_FAMILIES, "清淡", "清淡");
        family(ATTRIBUTE_FAMILIES, "微辣", "微辣");
        family(ATTRIBUTE_FAMILIES, "酸甜", "酸甜");
        family(ATTRIBUTE_FAMILIES, "咸鲜", "咸鲜");
    }

    private RecipePromptPolicy() { }

    static boolean matchesSearch(String query, RecipeStore.Row row, List<RecipeContracts.Component> components) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) return true;
        String searchable = searchable(row.title(), row.summary(), row.cuisine(), row.taste(), row.goal(),
                components.stream().map(RecipeContracts.Component::name).toList());
        if (searchable.contains(normalizedQuery)) return true;

        List<String> anchors = anchors(normalizedQuery, INGREDIENT_FAMILIES);
        anchors.addAll(anchors(normalizedQuery, METHOD_FAMILIES));
        anchors.addAll(anchors(normalizedQuery, ATTRIBUTE_FAMILIES));
        if (!anchors.isEmpty()) return anchors.stream().anyMatch(anchor -> containsFamily(searchable, anchor));

        for (String token : tokens(query)) if (searchable.contains(token)) return true;
        return false;
    }

    static boolean matchesGenerated(String prompt, RecipeGenerationPort.Draft draft) {
        String normalizedPrompt = normalize(prompt);
        if (normalizedPrompt.isBlank()) return true;
        List<String> details = new ArrayList<>(draft.ingredients().stream().filter(item -> !"SEASONING".equals(item.role()))
                .map(RecipeGenerationPort.DraftIngredient::name).toList());
        details.addAll(draft.steps());
        String searchable = searchable(draft.title(), draft.summary(), draft.cuisine(), draft.taste(), draft.goal(), details);

        List<String> ingredientAnchors = anchors(normalizedPrompt, INGREDIENT_FAMILIES);
        List<String> methodAnchors = anchors(normalizedPrompt, METHOD_FAMILIES);
        List<String> attributeAnchors = anchors(normalizedPrompt, ATTRIBUTE_FAMILIES);
        boolean ingredientsMatch = ingredientAnchors.stream().allMatch(anchor -> containsFamily(searchable, anchor));
        boolean methodsMatch = methodAnchors.stream().allMatch(anchor -> containsFamily(searchable, anchor));
        boolean attributesMatch = attributeAnchors.stream().allMatch(anchor -> containsFamily(searchable, anchor));
        if (!ingredientsMatch || !methodsMatch || !attributesMatch) return false;
        if (!ingredientAnchors.isEmpty() || !methodAnchors.isEmpty() || !attributeAnchors.isEmpty()) return true;

        String title = normalize(draft.title());
        if (title.contains(normalizedPrompt) || normalizedPrompt.contains(title)) return true;
        return tokens(prompt).stream().anyMatch(token -> token.length() >= 2 && searchable.contains(token));
    }

    private static boolean containsFamily(String searchable, String canonical) {
        return INGREDIENT_FAMILIES.getOrDefault(canonical,
                        METHOD_FAMILIES.getOrDefault(canonical, ATTRIBUTE_FAMILIES.getOrDefault(canonical, List.of(canonical))))
                .stream().map(RecipePromptPolicy::normalize).anyMatch(searchable::contains);
    }

    private static List<String> anchors(String prompt, Map<String, List<String>> families) {
        List<String> output = new ArrayList<>();
        families.forEach((canonical, aliases) -> {
            if (aliases.stream().map(RecipePromptPolicy::normalize).anyMatch(prompt::contains)) output.add(canonical);
        });
        return output;
    }

    private static List<String> tokens(String value) {
        return java.util.Arrays.stream(normalize(value).split("[\\s,，、。；;：:！!？?]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !List.of("想吃", "一道", "菜谱", "做法", "帮我", "请做", "生成", "晚餐", "午餐", "早餐").contains(token))
                .toList();
    }

    private static String searchable(String title, String summary, String cuisine, String taste, String goal, List<String> ingredients) {
        return normalize(String.join(" ", values(title, summary, cuisine, taste, goal, String.join(" ", ingredients))));
    }

    private static List<String> values(String... values) {
        List<String> output = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) output.add(value);
        return output;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static void family(Map<String, List<String>> output, String canonical, String... aliases) {
        output.put(canonical, List.of(aliases));
    }
}
