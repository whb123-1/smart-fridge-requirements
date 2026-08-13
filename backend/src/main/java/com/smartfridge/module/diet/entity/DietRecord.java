package com.smartfridge.module.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("diet_record")
public class DietRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate recordDate;
    private String mealType;
    private Long recipeId;
    private String customName;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal calorie;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carb;
    private LocalDateTime createdAt;
}
