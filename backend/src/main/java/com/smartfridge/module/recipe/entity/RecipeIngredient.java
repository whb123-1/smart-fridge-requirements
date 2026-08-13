package com.smartfridge.module.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("recipe_ingredient")
public class RecipeIngredient {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private String name;
    private BigDecimal quantity;
    private String unit;
    private Integer isEssential;
    private String alternative;
    private Integer isCondiment;
    private Integer isStaple;
    private BigDecimal minScale;
    private BigDecimal maxScale;
}
