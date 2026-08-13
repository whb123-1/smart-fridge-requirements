package com.smartfridge.module.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.BusinessException;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.entity.InventoryLog;
import com.smartfridge.module.food.mapper.FoodItemMapper;
import com.smartfridge.module.food.mapper.InventoryLogMapper;
import com.smartfridge.module.recipe.entity.Recipe;
import com.smartfridge.module.recipe.entity.RecipeHistory;
import com.smartfridge.module.recipe.entity.RecipeIngredient;
import com.smartfridge.module.recipe.entity.RecipeStep;
import com.smartfridge.module.recipe.entity.UserRecipeFavorite;
import com.smartfridge.module.recipe.mapper.RecipeHistoryMapper;
import com.smartfridge.module.recipe.mapper.RecipeIngredientMapper;
import com.smartfridge.module.recipe.mapper.RecipeMapper;
import com.smartfridge.module.recipe.mapper.RecipeStepMapper;
import com.smartfridge.module.recipe.mapper.UserRecipeFavoriteMapper;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final List<String> MEALS = List.of("早餐", "午餐", "晚餐");

    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper ingredientMapper;
    private final RecipeStepMapper stepMapper;
    private final UserRecipeFavoriteMapper favoriteMapper;
    private final RecipeHistoryMapper historyMapper;
    private final FoodItemMapper foodItemMapper;
    private final InventoryLogMapper logMapper;
    private final UserPreferenceMapper preferenceMapper;

    // ---------- 推荐 ----------
    public List<RecipeMatchVO> recommend(String keyword, Integer cookTimeMax, String taste, String dietGoal) {
        Map<String, List<FoodItem>> stock = stockMap();
        List<String> blocked = blockedFoods(UserContext.get());
        LambdaQueryWrapper<Recipe> qw = new LambdaQueryWrapper<Recipe>().eq(Recipe::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            qw.like(Recipe::getName, keyword);
        }
        if (cookTimeMax != null) {
            qw.le(Recipe::getCookTimeMin, cookTimeMax);
        }
        if (StringUtils.hasText(taste)) {
            qw.like(Recipe::getTaste, taste);
        }
        List<Recipe> recipes = recipeMapper.selectList(qw);
        List<RecipeMatchVO> result = new ArrayList<>();
        for (Recipe r : recipes) {
            List<RecipeIngredient> ingredients = ingredients(r.getId());
            if (ingredients.stream().anyMatch(i -> containsBlocked(i.getName(), blocked))) {
                continue;
            }
            RecipeMatchVO vo = match(r, ingredients, stock);
            if (StringUtils.hasText(dietGoal) && !passGoal(dietGoal, r)) {
                continue;
            }
            result.add(vo);
        }
        result.sort(Comparator.comparingInt(RecipeMatchVO::matchRank)
                .thenComparing(Comparator.comparingDouble(RecipeMatchVO::coverage).reversed())
                .thenComparing(RecipeMatchVO::id));
        return result;
    }

    // ---------- 多菜谱缺料检查 ----------
    public CheckResult checkSelected(CheckReq req) {
        if (req.recipeIds() == null || req.recipeIds().isEmpty()) {
            throw new BusinessException("请至少选择一道菜谱");
        }
        int servings = req.servings() == null || req.servings() <= 0 ? 1 : req.servings();
        Map<String, List<FoodItem>> stock = stockMap();
        Map<String, IngredientNeed> needMap = new LinkedHashMap<>();
        for (Long recipeId : req.recipeIds()) {
            Recipe recipe = requireRecipe(recipeId);
            for (RecipeIngredient ing : ingredients(recipeId)) {
                // 调味品视为厨房常备，不参与缺料检查
                if (ing.getIsCondiment() == 1) {
                    continue;
                }
                BigDecimal qty = ing.getQuantity()
                        .multiply(BigDecimal.valueOf(servings))
                        .divide(BigDecimal.valueOf(recipe.getServings()), 2, RoundingMode.HALF_UP);
                IngredientNeed need = needMap.computeIfAbsent(normalize(ing.getName()),
                        k -> new IngredientNeed(ing.getName(), ing.getUnit(), BigDecimal.ZERO));
                need.needed = need.needed.add(qty);
            }
        }
        List<MissingItem> items = new ArrayList<>();
        for (IngredientNeed need : needMap.values()) {
            List<FoodItem> haveItems = stock.getOrDefault(normalize(need.name), List.of());
            BigDecimal have = haveItems.isEmpty() ? BigDecimal.ZERO : haveItems.get(0).getQuantity();
            BigDecimal missing = need.needed.subtract(have).max(BigDecimal.ZERO);
            if (missing.compareTo(BigDecimal.ZERO) > 0) {
                items.add(new MissingItem(need.name, need.unit, need.needed, have, missing));
            }
        }
        return new CheckResult(items.isEmpty(), items);
    }

    // ---------- 详情 / 收藏 / 历史 ----------
    public RecipeDetailVO detail(Long recipeId) {
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new BusinessException(404, "菜谱不存在");
        }
        Map<String, List<FoodItem>> stock = stockMap();
        RecipeMatchVO match = match(recipe, ingredients(recipeId), stock);
        List<IngredientVO> ingredientVOs = ingredients(recipeId).stream()
                .map(i -> new IngredientVO(i.getName(), i.getQuantity(), i.getUnit(),
                        i.getIsEssential(), i.getAlternative(), i.getIsCondiment(), i.getIsStaple(),
                        hasStock(stock, i.getName()),
                        stock.getOrDefault(normalize(i.getName()), List.of()).isEmpty()
                                ? BigDecimal.ZERO
                                : stock.get(normalize(i.getName())).get(0).getQuantity(),
                        stock.getOrDefault(normalize(i.getName()), List.of()).isEmpty()
                                ? null
                                : stock.get(normalize(i.getName())).get(0).getUnit()))
                .toList();
        List<StepVO> stepVOs = stepMapper.selectList(new LambdaQueryWrapper<RecipeStep>()
                        .eq(RecipeStep::getRecipeId, recipeId)
                        .orderByAsc(RecipeStep::getStepNo))
                .stream()
                .map(s -> new StepVO(s.getStepNo(), s.getContent(), s.getCookMin()))
                .toList();
        boolean favorite = favoriteMapper.selectCount(new LambdaQueryWrapper<UserRecipeFavorite>()
                .eq(UserRecipeFavorite::getUserId, UserContext.get())
                .eq(UserRecipeFavorite::getRecipeId, recipeId)) > 0;
        return new RecipeDetailVO(recipe.getId(), recipe.getName(), recipe.getCuisine(), recipe.getTaste(),
                recipe.getCookTimeMin(), recipe.getDifficulty(), recipe.getServings(),
                recipe.getPerServingCalorie(), recipe.getDescription(), favorite,
                match.matchType(), match.matchText(), match.missingNames(), match.missingCondiments(),
                ingredientVOs, stepVOs, recipe.getCreatedBy());
    }

    public void favorite(Long recipeId) {
        if (recipeMapper.selectById(recipeId) == null) {
            throw new BusinessException(404, "菜谱不存在");
        }
        UserRecipeFavorite fav = new UserRecipeFavorite();
        fav.setUserId(UserContext.get());
        fav.setRecipeId(recipeId);
        favoriteMapper.insert(fav);
    }

    public void unfavorite(Long recipeId) {
        favoriteMapper.delete(new LambdaQueryWrapper<UserRecipeFavorite>()
                .eq(UserRecipeFavorite::getUserId, UserContext.get())
                .eq(UserRecipeFavorite::getRecipeId, recipeId));
    }

    /**
     * 删除菜谱（仅限当前用户自己生成的菜谱，系统内置菜谱不可删除）
     */
    public void delete(Long id) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BusinessException(404, "菜谱不存在");
        }
        if (recipe.getCreatedBy() == null || !recipe.getCreatedBy().equals(UserContext.get())) {
            throw new BusinessException(400, "只能删除自己生成的菜谱");
        }
        ingredientMapper.delete(new LambdaQueryWrapper<RecipeIngredient>()
                .eq(RecipeIngredient::getRecipeId, id));
        stepMapper.delete(new LambdaQueryWrapper<RecipeStep>()
                .eq(RecipeStep::getRecipeId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<UserRecipeFavorite>()
                .eq(UserRecipeFavorite::getRecipeId, id));
        historyMapper.delete(new LambdaQueryWrapper<RecipeHistory>()
                .eq(RecipeHistory::getRecipeId, id));
        recipeMapper.deleteById(id);
    }

    public List<RecipeMatchVO> favorites() {
        List<Long> ids = favoriteMapper.selectList(new LambdaQueryWrapper<UserRecipeFavorite>()
                        .eq(UserRecipeFavorite::getUserId, UserContext.get())
                        .orderByDesc(UserRecipeFavorite::getCreatedAt))
                .stream().map(UserRecipeFavorite::getRecipeId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, List<FoodItem>> stock = stockMap();
        List<String> blocked = blockedFoods(UserContext.get());
        return recipeMapper.selectBatchIds(ids).stream()
                .filter(r -> ingredients(r.getId()).stream().noneMatch(i -> containsBlocked(i.getName(), blocked)))
                .map(r -> match(r, ingredients(r.getId()), stock))
                .toList();
    }

    public List<HistoryVO> history() {
        return historyMapper.selectList(new LambdaQueryWrapper<RecipeHistory>()
                        .eq(RecipeHistory::getUserId, UserContext.get())
                        .orderByDesc(RecipeHistory::getCreatedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(h -> {
                    Recipe r = recipeMapper.selectById(h.getRecipeId());
                    return new HistoryVO(h.getRecipeId(),
                            r == null ? "已删除菜谱" : r.getName(),
                            h.getActionType(), actionText(h.getActionType()),
                            h.getServings(), h.getCreatedAt());
                })
                .toList();
    }

    // ---------- 用量动态调整 ----------
    public ScaleResultVO scale(Long recipeId, ScaleReq req) {
        Recipe recipe = requireRecipe(recipeId);
        List<RecipeIngredient> ingredients = ingredients(recipeId);
        RecipeIngredient main = ingredients.stream()
                .filter(i -> i.getIsCondiment() == 0 && i.getName().contains(req.mainName()))
                .findFirst()
                .orElseGet(() -> ingredients.stream().filter(i -> i.getIsCondiment() == 0).findFirst()
                        .orElseThrow(() -> new BusinessException("菜谱中没有可用作主料的食材")));
        if (req.actualQty() == null || req.actualQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("实际用量必须大于 0");
        }
        BigDecimal ratio = req.actualQty().divide(main.getQuantity(), 4, RoundingMode.HALF_UP);
        List<ScaledIngredient> scaled = ingredients.stream().map(i -> {
            BigDecimal scale;
            if (i.getIsCondiment() == 1) {
                double min = i.getMinScale() == null ? 0.5 : i.getMinScale().doubleValue();
                double max = i.getMaxScale() == null ? 1.5 : i.getMaxScale().doubleValue();
                scale = BigDecimal.valueOf(Math.max(min, Math.min(max, ratio.doubleValue())));
            } else {
                scale = ratio;
            }
            BigDecimal qty = i.getQuantity().multiply(scale).setScale(1, RoundingMode.HALF_UP);
            return new ScaledIngredient(i.getName(), qty, i.getUnit(), i.getIsCondiment() == 1);
        }).toList();
        BigDecimal newCalorie = recipe.getPerServingCalorie() == null ? BigDecimal.ZERO
                : recipe.getPerServingCalorie().multiply(ratio).setScale(1, RoundingMode.HALF_UP);
        return new ScaleResultVO(recipeId, ratio.setScale(2, RoundingMode.HALF_UP), newCalorie, scaled);
    }

    // ---------- 完成制作扣库存 ----------
    @Transactional
    public List<CookResultVO> cook(Long recipeId, CookReq req) {
        Recipe recipe = requireRecipe(recipeId);
        int servings = req.servings() == null ? 1 : req.servings();
        if (servings <= 0) {
            throw new BusinessException("份数必须大于 0");
        }
        List<CookResultVO> consumed = new ArrayList<>();
        for (RecipeIngredient ing : ingredients(recipeId)) {
            BigDecimal needed = ing.getQuantity()
                    .multiply(BigDecimal.valueOf(servings))
                    .divide(BigDecimal.valueOf(recipe.getServings()), 2, RoundingMode.HALF_UP);
            List<FoodItem> stock = foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                    .eq(FoodItem::getUserId, UserContext.get())
                    .eq(FoodItem::getStatus, "in_stock")
                    .eq(FoodItem::getName, ing.getName()));
            BigDecimal totalConsumed = BigDecimal.ZERO;
            for (FoodItem item : stock) {
                if (totalConsumed.compareTo(needed) >= 0) {
                    break;
                }
                BigDecimal take = item.getQuantity().min(needed.subtract(totalConsumed));
                item.setQuantity(item.getQuantity().subtract(take));
                if (item.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    item.setStatus("consumed");
                }
                boolean low = item.getLowStockThreshold() != null
                        && item.getQuantity().compareTo(item.getLowStockThreshold()) <= 0;
                item.setIsLowStock(low ? 1 : 0);
                foodItemMapper.updateById(item);
                InventoryLog log = new InventoryLog();
                log.setUserId(UserContext.get());
                log.setFoodItemId(item.getId());
                log.setFoodName(item.getName());
                log.setChangeType("consume");
                log.setChangeQty(take);
                log.setChangeUnit(item.getUnit());
                log.setAfterQty(item.getQuantity());
                log.setRelatedRecipeId(recipeId);
                log.setRemark("制作《" + recipe.getName() + "》");
                log.setCreatedAt(LocalDateTime.now());
                logMapper.insert(log);
                totalConsumed = totalConsumed.add(take);
            }
            if (totalConsumed.compareTo(BigDecimal.ZERO) > 0) {
                consumed.add(new CookResultVO(ing.getName(), totalConsumed, ing.getUnit()));
            }
        }
        history(recipeId, "cook", servings);
        return consumed;
    }

    // ---------- 内部工具 ----------
    private RecipeMatchVO match(Recipe r, List<RecipeIngredient> ingredients,
                                Map<String, List<FoodItem>> stock) {
        int total = ingredients.size();
        int available = 0;
        boolean alternativeOk = false;
        List<String> missingEssential = new ArrayList<>();
        List<String> missingCondiments = new ArrayList<>();
        for (RecipeIngredient ing : ingredients) {
            if (hasStock(stock, ing.getName())) {
                available++;
            } else if (ing.getIsCondiment() == 1) {
                missingCondiments.add(ing.getName());
            } else if (hasAlternative(ing, stock)) {
                alternativeOk = true;
                available++;
            } else {
                missingEssential.add(ing.getName());
            }
        }
        int rank;
        String type;
        String text;
        if (missingEssential.isEmpty()) {
            rank = 0;
            type = "can_make";
            text = missingCondiments.isEmpty() ? "可直接制作" : "可直接制作（调味品不足）";
        } else if (missingEssential.size() <= 2 && alternativeOk) {
            rank = 1;
            type = "alternative";
            text = "可使用替代食材制作";
        } else if (missingEssential.size() <= 2) {
            rank = 2;
            type = "missing_few";
            text = "缺少少量食材";
        } else {
            rank = 3;
            type = "other";
            text = "缺少较多食材";
        }
        double coverage = total == 0 ? 0 : available * 1.0 / total;
        return new RecipeMatchVO(r.getId(), r.getName(), r.getCuisine(), r.getTaste(),
                r.getCookTimeMin(), r.getDifficulty(), r.getServings(), r.getPerServingCalorie(),
                r.getDescription(), rank, type, text, coverage, missingEssential, missingCondiments);
    }

    private boolean hasAlternative(RecipeIngredient ing, Map<String, List<FoodItem>> stock) {
        if (!StringUtils.hasText(ing.getAlternative())) {
            return false;
        }
        for (String alt : ing.getAlternative().split("[,，]")) {
            if (hasStock(stock, alt.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean passGoal(String goal, Recipe r) {
        BigDecimal cal = r.getPerServingCalorie();
        if (cal == null) {
            return true;
        }
        return switch (goal) {
            case "减脂" -> cal.doubleValue() <= 450;
            case "控制热量" -> cal.doubleValue() <= 500;
            case "增肌" -> cal.doubleValue() >= 250;
            default -> true;
        };
    }

    private Map<String, List<FoodItem>> stockMap() {
        Map<String, List<FoodItem>> map = new HashMap<>();
        foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                        .eq(FoodItem::getUserId, UserContext.get())
                        .eq(FoodItem::getStatus, "in_stock")
                        .gt(FoodItem::getQuantity, BigDecimal.ZERO))
                .forEach(item -> map.computeIfAbsent(normalize(item.getName()), k -> new ArrayList<>())
                        .add(item));
        return map;
    }

    private List<String> blockedFoods(Long userId) {
        UserPreference p = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, userId));
        List<String> blocked = new ArrayList<>();
        if (p != null) {
            split(p.getAllergy(), blocked);
            split(p.getAvoidFoods(), blocked);
        }
        return blocked;
    }

    private void split(String text, List<String> target) {
        if (StringUtils.hasText(text)) {
            for (String s : text.split("[,，、;；]")) {
                if (StringUtils.hasText(s)) {
                    target.add(s.trim());
                }
            }
        }
    }

    private boolean containsBlocked(String name, List<String> blocked) {
        if (name == null || blocked.isEmpty()) {
            return false;
        }
        for (String b : blocked) {
            if (name.contains(b) || b.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStock(Map<String, List<FoodItem>> stock, String name) {
        String n = normalize(name);
        List<FoodItem> exact = stock.get(n);
        if (exact != null && exact.stream().anyMatch(i -> i.getQuantity().compareTo(BigDecimal.ZERO) > 0)) {
            return true;
        }
        // 模糊匹配：库存名包含食材名（如库存“鸡腿肉”可匹配菜谱里的“鸡腿”）
        if (n.length() >= 2) {
            for (Map.Entry<String, List<FoodItem>> entry : stock.entrySet()) {
                if (entry.getKey().contains(n)
                        && entry.getValue().stream().anyMatch(i -> i.getQuantity().compareTo(BigDecimal.ZERO) > 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private List<RecipeIngredient> ingredients(Long recipeId) {
        return ingredientMapper.selectList(new LambdaQueryWrapper<RecipeIngredient>()
                .eq(RecipeIngredient::getRecipeId, recipeId)
                .orderByAsc(RecipeIngredient::getIsCondiment)
                .orderByAsc(RecipeIngredient::getId));
    }

    private Recipe requireRecipe(Long id) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BusinessException(404, "菜谱不存在");
        }
        return recipe;
    }

    private void history(Long recipeId, String actionType, Integer servings) {
        RecipeHistory h = new RecipeHistory();
        h.setUserId(UserContext.get());
        h.setRecipeId(recipeId);
        h.setActionType(actionType);
        h.setServings(servings);
        h.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(h);
    }

    private String actionText(String type) {
        return switch (type) {
            case "browse" -> "浏览";
            case "generate" -> "生成";
            case "cook" -> "制作完成";
            default -> type;
        };
    }

    // ---------- VO / DTO ----------
    public record RecipeMatchVO(
            Long id, String name, String cuisine, String taste,
            Integer cookTimeMin, String difficulty, Integer servings,
            BigDecimal perServingCalorie, String description,
            int matchRank, String matchType, String matchText, double coverage,
            List<String> missingNames, List<String> missingCondiments) {
    }

    public record IngredientVO(
            String name, BigDecimal quantity, String unit,
            Integer isEssential, String alternative, Integer isCondiment, Integer isStaple,
            boolean available, BigDecimal stockQty, String stockUnit) {
    }

    public record StepVO(Integer stepNo, String content, Integer cookMin) {
    }

    public record RecipeDetailVO(
            Long id, String name, String cuisine, String taste,
            Integer cookTimeMin, String difficulty, Integer servings,
            BigDecimal perServingCalorie, String description,
            boolean favorite, String matchType, String matchText,
            List<String> missingNames, List<String> missingCondiments,
            List<IngredientVO> ingredients, List<StepVO> steps, Long createdBy) {
    }

    public record ScaleReq(String mainName, BigDecimal actualQty) {
    }

    public record ScaledIngredient(String name, BigDecimal quantity, String unit, boolean isCondiment) {
    }

    public record ScaleResultVO(Long recipeId, BigDecimal ratio, BigDecimal newPerServingCalorie,
                                List<ScaledIngredient> ingredients) {
    }

    public record CookReq(Integer servings) {
    }

    public record CookResultVO(String name, BigDecimal consumedQty, String unit) {
    }

    public record CheckReq(List<Long> recipeIds, Integer servings) {
    }

    public record MissingItem(String name, String unit, BigDecimal needed, BigDecimal have,
                              BigDecimal missing) {
    }

    public record CheckResult(boolean ok, List<MissingItem> items) {
    }

    public record HistoryVO(Long recipeId, String recipeName, String actionType, String actionText,
                            Integer servings, LocalDateTime createdAt) {
    }

    private static class IngredientNeed {
        final String name;
        final String unit;
        BigDecimal needed;

        IngredientNeed(String name, String unit, BigDecimal needed) {
            this.name = name;
            this.unit = unit;
            this.needed = needed;
        }
    }
}
