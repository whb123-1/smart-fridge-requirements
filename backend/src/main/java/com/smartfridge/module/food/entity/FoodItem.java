package com.smartfridge.module.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("food_item")
public class FoodItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long zoneId;
    private Long categoryId;
    private String name;
    private BigDecimal quantity;
    private String unit;
    private String unitType;
    private String status;
    private LocalDate entryDate;
    private LocalDate openedDate;
    private LocalDate packageExpiryDate;
    private LocalDate suggestedExpiryDate;
    private String expiryBasis;
    private BigDecimal lowStockThreshold;
    private Integer isLowStock;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
