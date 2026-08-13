package com.smartfridge.module.shopping.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.BusinessException;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.mapper.FoodItemMapper;
import com.smartfridge.module.recipe.entity.Recipe;
import com.smartfridge.module.recipe.entity.RecipeIngredient;
import com.smartfridge.module.recipe.mapper.RecipeIngredientMapper;
import com.smartfridge.module.recipe.mapper.RecipeMapper;
import com.smartfridge.module.shopping.entity.ShoppingList;
import com.smartfridge.module.shopping.entity.ShoppingListItem;
import com.smartfridge.module.shopping.mapper.ShoppingListItemMapper;
import com.smartfridge.module.shopping.mapper.ShoppingListMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShoppingService {

    private final ShoppingListMapper listMapper;
    private final ShoppingListItemMapper itemMapper;
    private final FoodItemMapper foodItemMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper ingredientMapper;

    public List<ListVO> lists() {
        List<ShoppingList> lists = listMapper.selectList(new LambdaQueryWrapper<ShoppingList>()
                .eq(ShoppingList::getUserId, UserContext.get())
                .orderByDesc(ShoppingList::getCreatedAt));
        return lists.stream().map(this::toVO).toList();
    }

    public ShoppingList create(String name) {
        ShoppingList list = new ShoppingList();
        list.setUserId(UserContext.get());
        list.setName(StringUtils.hasText(name) ? name : "购物清单");
        list.setStatus("pending");
        list.setSourceType("manual");
        listMapper.insert(list);
        return list;
    }

    @Transactional
    public ListVO auto() {
        Long userId = UserContext.get();
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        // 低库存食材
        foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                        .eq(FoodItem::getUserId, userId)
                        .eq(FoodItem::getStatus, "in_stock")
                        .isNotNull(FoodItem::getLowStockThreshold)
                        .apply("quantity <= low_stock_threshold"))
                .forEach(item -> {
                    BigDecimal need = item.getLowStockThreshold()
                            .multiply(new BigDecimal("2"))
                            .subtract(item.getQuantity())
                            .max(BigDecimal.ONE)
                            .setScale(1, RoundingMode.HALF_UP);
                    candidates.merge(normalize(item.getName()),
                            new Candidate(item.getName(), need, item.getUnit(), item.getCategoryId(), "库存不足"),
                            (a, b) -> new Candidate(a.name(), a.quantity().max(b.quantity()), a.unit(),
                                    a.categoryId(), "库存不足"));
                });

        // 制作菜谱缺少的必需食材
        Map<String, BigDecimal> stock = stockMap(userId);
        recipeMapper.selectList(new LambdaQueryWrapper<Recipe>().eq(Recipe::getStatus, 1))
                .forEach(recipe -> {
                    int missingCount = 0;
                    List<String> missing = new ArrayList<>();
                    for (RecipeIngredient ing : ingredients(recipe.getId())) {
                        if (ing.getIsCondiment() == 1) {
                            continue;
                        }
                        if (stock.getOrDefault(normalize(ing.getName()), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
                            missingCount++;
                            missing.add(ing.getName());
                        }
                    }
                    if (missingCount > 0 && missingCount <= 2) {
                        for (String name : missing) {
                            candidates.merge(normalize(name),
                                    new Candidate(name, BigDecimal.ONE, "份", null,
                                            "制作《" + recipe.getName() + "》缺料"),
                                    (a, b) -> new Candidate(a.name(), a.quantity(), a.unit(),
                                            a.categoryId(), a.remark() + "；" + b.remark()));
                        }
                    }
                });

        if (candidates.isEmpty()) {
            throw new BusinessException("当前库存充足，无需购买");
        }

        ShoppingList list = new ShoppingList();
        list.setUserId(userId);
        list.setName("自动购物清单 " + LocalDate.now());
        list.setStatus("pending");
        list.setSourceType("auto");
        listMapper.insert(list);
        candidates.values().forEach(c -> {
            ShoppingListItem item = new ShoppingListItem();
            item.setListId(list.getId());
            item.setFoodName(c.name());
            item.setCategoryId(c.categoryId());
            item.setQuantity(c.quantity());
            item.setUnit(c.unit());
            item.setPurchased(0);
            item.setRemark(c.remark());
            itemMapper.insert(item);
        });
        return toVO(list);
    }

    public ShoppingListItem addItem(Long listId, ItemReq req) {
        ShoppingList list = owned(listId);
        if (!StringUtils.hasText(req.foodName())) {
            throw new BusinessException("物品名称不能为空");
        }
        ShoppingListItem item = new ShoppingListItem();
        item.setListId(list.getId());
        item.setFoodName(req.foodName().trim());
        item.setCategoryId(req.categoryId());
        item.setQuantity(req.quantity() == null ? BigDecimal.ONE : req.quantity());
        item.setUnit(StringUtils.hasText(req.unit()) ? req.unit() : "个");
        item.setPurchased(0);
        item.setRemark(req.remark());
        itemMapper.insert(item);
        refreshStatus(list.getId());
        return item;
    }

    public ShoppingListItem updateItem(Long itemId, ItemUpdateReq req) {
        ShoppingListItem item = ownedItem(itemId);
        if (req.quantity() != null && req.quantity().compareTo(BigDecimal.ZERO) > 0) {
            item.setQuantity(req.quantity());
        }
        if (req.purchased() != null) {
            item.setPurchased(req.purchased() == 1 ? 1 : 0);
        }
        if (StringUtils.hasText(req.remark())) {
            item.setRemark(req.remark());
        }
        itemMapper.updateById(item);
        refreshStatus(item.getListId());
        return item;
    }

    public void removeItem(Long itemId) {
        ShoppingListItem item = ownedItem(itemId);
        itemMapper.deleteById(itemId);
        refreshStatus(item.getListId());
    }

    public void deleteList(Long listId) {
        ShoppingList list = owned(listId);
        itemMapper.delete(new LambdaQueryWrapper<ShoppingListItem>()
                .eq(ShoppingListItem::getListId, list.getId()));
        listMapper.deleteById(listId);
    }

    private void refreshStatus(Long listId) {
        List<ShoppingListItem> items = itemMapper.selectList(new LambdaQueryWrapper<ShoppingListItem>()
                .eq(ShoppingListItem::getListId, listId));
        ShoppingList list = listMapper.selectById(listId);
        if (list == null) {
            return;
        }
        String status;
        if (items.isEmpty()) {
            status = "pending";
        } else if (items.stream().allMatch(i -> i.getPurchased() == 1)) {
            status = "done";
        } else if (items.stream().anyMatch(i -> i.getPurchased() == 1)) {
            status = "partial";
        } else {
            status = "pending";
        }
        list.setStatus(status);
        listMapper.updateById(list);
    }

    private ListVO toVO(ShoppingList list) {
        List<ItemVO> items = itemMapper.selectList(new LambdaQueryWrapper<ShoppingListItem>()
                        .eq(ShoppingListItem::getListId, list.getId())
                        .orderByAsc(ShoppingListItem::getPurchased)
                        .orderByAsc(ShoppingListItem::getId))
                .stream()
                .map(i -> new ItemVO(i.getId(), i.getFoodName(), i.getCategoryId(), i.getQuantity(),
                        i.getUnit(), i.getPurchased(), i.getSourceRecipeId(), i.getRemark()))
                .toList();
        return new ListVO(list.getId(), list.getName(), list.getStatus(), list.getSourceType(),
                list.getCreatedAt(), items);
    }

    private ShoppingList owned(Long id) {
        ShoppingList list = listMapper.selectById(id);
        if (list == null || !list.getUserId().equals(UserContext.get())) {
            throw new BusinessException(404, "购物清单不存在");
        }
        return list;
    }

    private ShoppingListItem ownedItem(Long id) {
        ShoppingListItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "购物项不存在");
        }
        owned(item.getListId());
        return item;
    }

    private List<RecipeIngredient> ingredients(Long recipeId) {
        return ingredientMapper.selectList(new LambdaQueryWrapper<RecipeIngredient>()
                .eq(RecipeIngredient::getRecipeId, recipeId));
    }

    private Map<String, BigDecimal> stockMap(Long userId) {
        Map<String, BigDecimal> map = new HashMap<>();
        foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                        .eq(FoodItem::getUserId, userId)
                        .eq(FoodItem::getStatus, "in_stock")
                        .gt(FoodItem::getQuantity, BigDecimal.ZERO))
                .forEach(item -> map.merge(normalize(item.getName()), item.getQuantity(), BigDecimal::add));
        return map;
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private record Candidate(String name, BigDecimal quantity, String unit, Long categoryId, String remark) {
    }

    public record ItemReq(String foodName, Long categoryId, BigDecimal quantity, String unit, String remark) {
    }

    public record ItemUpdateReq(Integer purchased, BigDecimal quantity, String remark) {
    }

    public record ItemVO(Long id, String foodName, Long categoryId, BigDecimal quantity,
                         String unit, Integer purchased, Long sourceRecipeId, String remark) {
    }

    public record ListVO(Long id, String name, String status, String sourceType,
                         java.time.LocalDateTime createdAt, List<ItemVO> items) {
    }
}
