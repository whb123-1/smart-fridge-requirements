package com.smartfridge.module.diet.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.BusinessException;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.diet.entity.DietRecord;
import com.smartfridge.module.diet.mapper.DietRecordMapper;
import com.smartfridge.module.recipe.entity.Recipe;
import com.smartfridge.module.recipe.mapper.RecipeMapper;
import com.smartfridge.module.user.entity.UserPreference;
import com.smartfridge.module.user.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DietService {

    private final DietRecordMapper dietRecordMapper;
    private final RecipeMapper recipeMapper;
    private final UserPreferenceMapper preferenceMapper;

    public DietRecord add(DietReq req) {
        LocalDate date = req.recordDate() == null ? LocalDate.now() : req.recordDate();
        BigDecimal qty = req.quantity() == null ? BigDecimal.ONE : req.quantity();
        DietRecord record = new DietRecord();
        record.setUserId(UserContext.get());
        record.setRecordDate(date);
        record.setMealType(StringUtils.hasText(req.mealType()) ? req.mealType() : "午餐");
        record.setQuantity(qty);
        record.setUnit(StringUtils.hasText(req.unit()) ? req.unit() : "份");
        if (req.recipeId() != null) {
            Recipe recipe = recipeMapper.selectById(req.recipeId());
            if (recipe == null) {
                throw new BusinessException(404, "菜谱不存在");
            }
            record.setRecipeId(recipe.getId());
            record.setCustomName(recipe.getName());
            BigDecimal cal = recipe.getPerServingCalorie() == null ? BigDecimal.ZERO : recipe.getPerServingCalorie();
            record.setCalorie(cal.multiply(qty).setScale(1, java.math.RoundingMode.HALF_UP));
        } else {
            if (!StringUtils.hasText(req.customName())) {
                throw new BusinessException("请选择菜谱或填写食物名称");
            }
            record.setCustomName(req.customName().trim());
            record.setCalorie(req.calorie() == null ? BigDecimal.ZERO : req.calorie());
            record.setProtein(req.protein());
            record.setFat(req.fat());
            record.setCarb(req.carb());
        }
        dietRecordMapper.insert(record);
        return record;
    }

    public void remove(Long id) {
        DietRecord record = dietRecordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(UserContext.get())) {
            throw new BusinessException(404, "饮食记录不存在");
        }
        dietRecordMapper.deleteById(id);
    }

    public List<DietRecord> list(LocalDate date) {
        return dietRecordMapper.selectList(new LambdaQueryWrapper<DietRecord>()
                .eq(DietRecord::getUserId, UserContext.get())
                .eq(DietRecord::getRecordDate, date == null ? LocalDate.now() : date)
                .orderByAsc(DietRecord::getId));
    }

    public DailySummary summary(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        List<DietRecord> records = list(d);
        BigDecimal cal = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal carb = BigDecimal.ZERO;
        Map<String, BigDecimal> byMeal = new LinkedHashMap<>();
        for (DietRecord r : records) {
            cal = cal.add(nz(r.getCalorie()));
            protein = protein.add(nz(r.getProtein()));
            fat = fat.add(nz(r.getFat()));
            carb = carb.add(nz(r.getCarb()));
            byMeal.merge(r.getMealType(), nz(r.getCalorie()), BigDecimal::add);
        }
        List<MealSummary> meals = byMeal.entrySet().stream()
                .map(e -> new MealSummary(e.getKey(), e.getValue()))
                .toList();
        UserPreference p = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, UserContext.get()));
        String advice = buildAdvice(records.size(), cal, protein, fat, p);
        return new DailySummary(d, cal, protein, fat, carb, records.size(), meals, advice);
    }

    private String buildAdvice(int count, BigDecimal cal, BigDecimal protein, BigDecimal fat,
                               UserPreference preference) {
        List<String> tips = new ArrayList<>();
        double calD = cal.doubleValue();
        if (calD > 0 && fat.doubleValue() > 0 && fat.doubleValue() / calD > 0.3) {
            tips.add("今日脂肪供能占比较高，建议减少油炸与高油食物");
        }
        if (calD > 0 && protein.doubleValue() < 60) {
            tips.add("蛋白质摄入偏低，建议补充鸡蛋、牛奶或瘦肉");
        }
        if (count < 3) {
            tips.add("今日用餐次数较少，建议规律三餐");
        }
        if (preference != null && preference.getTargetCalories() != null && preference.getTargetCalories() > 0) {
            int target = preference.getTargetCalories();
            if (calD > target * 1.2) {
                tips.add(String.format("已超出目标热量约 %d 千卡，建议控制晚餐分量", (int) (calD - target)));
            } else if (calD > 0 && calD < target * 0.5) {
                tips.add("今日摄入热量偏低，注意保证基本能量");
            }
        }
        if (tips.isEmpty()) {
            return "今日饮食比较均衡，继续保持。";
        }
        return String.join("；", tips) + "。";
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public record DietReq(
            LocalDate recordDate,
            String mealType,
            Long recipeId,
            String customName,
            BigDecimal quantity,
            String unit,
            BigDecimal calorie,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carb) {
    }

    public record MealSummary(String mealType, BigDecimal calorie) {
    }

    public record DailySummary(
            LocalDate date,
            BigDecimal totalCalorie,
            BigDecimal totalProtein,
            BigDecimal totalFat,
            BigDecimal totalCarb,
            int mealCount,
            List<MealSummary> byMeal,
            String advice) {
    }
}
