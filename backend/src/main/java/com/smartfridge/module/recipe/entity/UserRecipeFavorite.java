package com.smartfridge.module.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_recipe_favorite")
public class UserRecipeFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long recipeId;
    private LocalDateTime createdAt;
}
