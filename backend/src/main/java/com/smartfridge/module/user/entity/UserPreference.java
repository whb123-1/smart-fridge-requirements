package com.smartfridge.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_preference")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String taste;
    private String allergy;
    private String avoidFoods;
    private String dietGoal;
    private Integer targetCalories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
