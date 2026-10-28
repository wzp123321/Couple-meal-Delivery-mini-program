package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 吃饭打卡主体（饭桌相册）。
 * 见 db/schema.sql meal_checkin 表：uk_meal_checkin_plan 保证一个约定只打一次卡。
 */
@Data
@TableName("meal_checkin")
public class MealCheckin implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long planId;
    private Long coupleId;
    private Long userId;
    private Integer rating;
    private String content;
    /** 带饭方回应 ≤50 字，覆盖式更新 */
    private String reply;
    /** 回应人 user_id */
    private Long replyBy;
    /** 回应时间 */
    private LocalDateTime replyTime;
    private Integer auditStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
