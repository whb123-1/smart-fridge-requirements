package com.smartfridge.module.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recipe")
public class Recipe {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String coverUrl;
    private String cuisine;
    private String taste;
    private Integer cookTimeMin;
    private String difficulty;
    private Integer servings;
    private BigDecimal perServingCalorie;
    private String description;
    private Long createdBy;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
