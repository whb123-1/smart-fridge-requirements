package com.smartfridge.module.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("food_category")
public class FoodCategory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private String itemType;
    private String defaultUnit;
    private String unitType;
    private Integer shelfLifeDays;
    private Integer openedDays;
    private BigDecimal per100gCalorie;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carb;
    private String icon;
    private Integer sort;
    private LocalDateTime createdAt;
}
