package com.smartfridge.module.shopping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shopping_list_item")
public class ShoppingListItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long listId;
    private String foodName;
    private Long categoryId;
    private BigDecimal quantity;
    private String unit;
    private Integer purchased;
    private Long sourceRecipeId;
    private String remark;
    private LocalDateTime createdAt;
}
