package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 打卡图片明细（仅被 10-关系绑定/后端.md §3.5 账号注销的「清理本人打卡图片」使用；
 * 图片审核的完整业务逻辑属模块 60，不在 M1 范围）。
 * 见 db/schema.sql meal_checkin_image 表（本实体只取注销所需的列）。
 */
@Data
@TableName("meal_checkin_image")
public class MealCheckinImage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long checkinId;
    private String url;
    private String thumbUrl;
    private Integer seq;
    private Integer auditStatus;
    private String traceId;
    private LocalDateTime auditedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
