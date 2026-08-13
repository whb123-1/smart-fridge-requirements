package com.smartfridge.module.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("recipe_step")
public class RecipeStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Integer stepNo;
    private String content;
    private Integer cookMin;
}
