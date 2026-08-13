package com.smartfridge.module.zone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fridge_zone")
public class FridgeZone {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String zoneType;
    private BigDecimal targetTemp;
    private BigDecimal targetHumidity;
    private String tempUnit;
    private BigDecimal minTemp;
    private BigDecimal maxTemp;
    private BigDecimal minHumidity;
    private BigDecimal maxHumidity;
    private String status;
    private LocalDateTime lastRecordAt;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
