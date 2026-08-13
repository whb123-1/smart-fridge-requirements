package com.smartfridge.module.zone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("zone_record")
public class ZoneRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long zoneId;
    private BigDecimal tempC;
    private BigDecimal humidity;
    private String source;
    private LocalDateTime recordTime;
    private Integer abnormalSeconds;
    private LocalDateTime createdAt;
}
