package com.smartfridge.module.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_history")
public class RecipeHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long recipeId;
    private String actionType;
    private Integer servings;
    private String note;
    private LocalDateTime createdAt;
}
