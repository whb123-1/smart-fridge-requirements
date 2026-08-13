package com.smartfridge.module.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("food_estimate")
public class FoodEstimate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String unit;
    private BigDecimal weightGrams;
    private Long categoryId;
}
