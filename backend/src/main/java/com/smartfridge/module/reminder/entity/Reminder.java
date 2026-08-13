package com.smartfridge.module.reminder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reminder")
public class Reminder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long foodItemId;
    private Long zoneId;
    private String type;
    private String title;
    private String content;
    private LocalDateTime remindTime;
    private Integer isRead;
    private String status;
    private LocalDateTime createdAt;
}
