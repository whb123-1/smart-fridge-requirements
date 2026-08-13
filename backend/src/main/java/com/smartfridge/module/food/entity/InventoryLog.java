package com.smartfridge.module.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_log")
public class InventoryLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long foodItemId;
    private String foodName;
    private String changeType;
    private BigDecimal changeQty;
    private String changeUnit;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private Long relatedRecipeId;
    private String remark;
    private LocalDateTime createdAt;
}
