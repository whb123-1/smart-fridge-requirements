package com.smartfridge.module.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.mapper.FoodItemMapper;
import com.smartfridge.module.recipe.entity.Recipe;
import com.smartfridge.module.recipe.entity.RecipeHistory;
import com.smartfridge.module.recipe.entity.RecipeIngredient;
import com.smartfridge.module.recipe.entity.RecipeStep;
import com.smartfridge.module.recipe.mapper.RecipeHistoryMapper;
import com.smartfridge.module.recipe.mapper.RecipeIngredientMapper;
import com.smartfridge.module.recipe.mapper.RecipeMapper;
import com.smartfridge.module.recipe.mapper.RecipeStepMapper;
import com.smartfridge.module.user.entity.UserPreference;
import com.smartfridge.module.user.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 DeepSeek 大模型的菜谱推荐与生成
 */
@Service
@RequiredArgsConstructor
public class AiRecipeService {

    private final DeepSeekService deepSeekService;
    private final FoodItemMapper foodItemMapper;
    private final UserPreferenceMapper preferenceMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper ingredientMapper;
    private final RecipeStepMapper stepMapper;
    private final RecipeHistoryMapper historyMapper;
    private final RecipeService recipeService;

    public List<AiRecommendVO> recommend(String focus) {
        String system = "你是一个专业的中餐菜谱助手，擅长利用冰箱现有食材推荐可做的菜。"
                + "你只输出 JSON，不要输出任何其他内容。";
        String focusText = StringUtils.hasText(focus)
                ? "\n用户指定主料：" + focus.trim()
                + "。请围绕它推荐菜谱，主料必须是它或与它同类的食材，"
                + "并尽量搭配库存中的其他食材（例如有鸡肉和粉条，就推荐鸡肉配粉条的菜）。"
                : "";
        String user = "冰箱现有库存：\n" + inventoryText()
                + "\n用户饮食偏好：\n" + preferenceText()
                + focusText
                + "\n请结合库存推荐 5 道菜。要求："
                + "①主料必须来自上面的库存（例如库存里有鸡肉就围绕鸡肉推荐鸡肉菜），"
                + "②缺少的食材尽量少，③避开忌口和过敏食材，④菜名体现主食材，"
                + "⑤库存里适合这道菜的食材都要尽量用上，不要无故遗漏（例如有鸡肉和粉条就推荐小鸡炖粉条）。"
                + "严格按以下 JSON 格式输出："
                + "{\"recipes\":[{\"name\":\"菜名\",\"reason\":\"推荐理由\","
                + "\"ingredients\":[\"用到的主食材\"],\"missing\":[\"缺少的食材\"],"
                + "\"cookTimeMin\":30,\"perServingCalorie\":300}]}";
        JsonNode root = deepSeekService.chatJson(system, user);
        List<AiRecommendVO> result = new ArrayList<>();
        for (JsonNode r : root.path("recipes")) {
            result.add(new AiRecommendVO(
                    r.path("name").asText("未命名"),
                    r.path("reason").asText(""),
                    toStringList(r.path("ingredients")),
                    toStringList(r.path("missing")),
                    r.path("cookTimeMin").asInt(30),
                    r.path("perServingCalorie").asDouble(0)));
        }
        return result;
    }

    @Transactional
    public RecipeService.RecipeDetailVO generate(String name) {
        String system = "你是一个专业的中餐菜谱生成器。你只输出 JSON，不要输出任何其他内容。";
        String target = StringUtils.hasText(name)
                ? "用户指定的主料或菜名：" + name.trim()
                : "菜名由你根据库存决定（一道能做的家常菜）";
        String user = "冰箱现有库存：\n" + inventoryText()
                + "\n用户饮食偏好：\n" + preferenceText()
                + "\n" + target
                + "\n（如果上面输入的是食材名如“鸡肉”，请围绕它另起一个合适的菜名并生成，"
                + "不要直接用“鸡肉”两个字当菜名。）"
                + "\n请生成完整菜谱。要求：主料必须来自上面的冰箱库存"
                + "（例如库存里有鸡肉就做以鸡肉为主的菜），调味品可以自由补充，"
                + "主料不能使用库存里没有的食材；库存里适合这道菜的食材都要尽量用上、不要遗漏"
                + "（例如库存有鸡肉和粉条，就应把粉条作为配菜写进食材清单）。"
                + "数量单位：库存里的数量按常见重量估算（如1个鸡腿约200克、1个土豆约150克），"
                + "不要把“1个”写成“1克”。"
                + "严格按以下 JSON 格式输出："
                + "{\"name\":\"菜名\",\"cuisine\":\"菜系\",\"taste\":\"口味\",\"cookTimeMin\":15,"
                + "\"servings\":2,\"perServingCalorie\":220,\"description\":\"简介\","
                + "\"ingredients\":[{\"name\":\"食材\",\"quantity\":200,\"unit\":\"克\","
                + "\"isStaple\":true,\"isCondiment\":false,\"alternative\":\"替代品或空\"}],"
                + "\"steps\":[{\"stepNo\":1,\"content\":\"步骤\",\"cookMin\":3}]}";
        JsonNode root = deepSeekService.chatJson(system, user);

        Recipe recipe = new Recipe();
        recipe.setName(root.path("name").asText("AI 菜谱"));
        recipe.setCuisine(root.path("cuisine").asText("家常菜"));
        recipe.setTaste(root.path("taste").asText("清淡"));
        recipe.setCookTimeMin(root.path("cookTimeMin").asInt(20));
        recipe.setDifficulty("简单");
        recipe.setServings(root.path("servings").asInt(2));
        recipe.setPerServingCalorie(BigDecimal.valueOf(root.path("perServingCalorie").asDouble(0))
                .setScale(1, RoundingMode.HALF_UP));
        recipe.setDescription(root.path("description").asText("AI 生成菜谱"));
        recipe.setCreatedBy(UserContext.get());
        recipe.setStatus(1);
        recipeMapper.insert(recipe);

        for (JsonNode ing : root.path("ingredients")) {
            boolean condiment = ing.path("isCondiment").asBoolean(false);
            RecipeIngredient i = new RecipeIngredient();
            i.setRecipeId(recipe.getId());
            i.setName(ing.path("name").asText("食材"));
            i.setQuantity(BigDecimal.valueOf(ing.path("quantity").asDouble(100))
                    .setScale(2, RoundingMode.HALF_UP));
            i.setUnit(ing.path("unit").asText("克"));
            i.setIsEssential(condiment ? 0 : 1);
            i.setAlternative(ing.path("alternative").asText(""));
            i.setIsCondiment(condiment ? 1 : 0);
            i.setIsStaple(ing.path("isStaple").asBoolean(false) ? 1 : 0);
            ingredientMapper.insert(i);
        }

        int no = 0;
        for (JsonNode step : root.path("steps")) {
            RecipeStep s = new RecipeStep();
            s.setRecipeId(recipe.getId());
            s.setStepNo(step.path("stepNo").asInt(++no));
            s.setContent(step.path("content").asText(""));
            s.setCookMin(step.path("cookMin").isMissingNode() || step.path("cookMin").isNull()
                    ? null : step.path("cookMin").asInt());
            stepMapper.insert(s);
        }

        RecipeHistory h = new RecipeHistory();
        h.setUserId(UserContext.get());
        h.setRecipeId(recipe.getId());
        h.setActionType("generate");
        h.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(h);
        return recipeService.detail(recipe.getId());
    }

    private String inventoryText() {
        List<FoodItem> items = foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, UserContext.get())
                .eq(FoodItem::getStatus, "in_stock")
                .gt(FoodItem::getQuantity, BigDecimal.ZERO)
                .orderByDesc(FoodItem::getQuantity)
                .last("LIMIT 30"));
        if (items.isEmpty()) {
            return "（冰箱为空）";
        }
        StringBuilder sb = new StringBuilder("（数量单位请按原样理解，不要自行换算）\n");
        for (FoodItem item : items) {
            sb.append("- ").append(item.getName()).append(" ")
                    .append(item.getQuantity()).append(item.getUnit()).append("\n");
        }
        return sb.toString();
    }

    private String preferenceText() {
        UserPreference p = preferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, UserContext.get()));
        if (p == null) {
            return "无特殊偏好";
        }
        return "口味：" + (p.getTaste() == null ? "无" : p.getTaste())
                + "；忌口：" + (p.getAvoidFoods() == null ? "无" : p.getAvoidFoods())
                + "；过敏：" + (p.getAllergy() == null ? "无" : p.getAllergy())
                + "；饮食目标：" + (p.getDietGoal() == null ? "均衡" : p.getDietGoal());
    }

    private List<String> toStringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        for (JsonNode n : arr) {
            list.add(n.asText());
        }
        return list;
    }

    public record AiRecommendVO(String name, String reason, List<String> ingredients,
                                List<String> missing, int cookTimeMin, double perServingCalorie) {
    }
}
