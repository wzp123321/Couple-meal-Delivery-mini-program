package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 爱心值流水（仅被 10-关系绑定/后端.md §3.7 关系主页读取累计值使用；
 * 爱心值的写入与幂等等完整业务逻辑属模块 40，不在 M1 范围）。
 * 见 db/schema.sql love_value 表。
 */
@Data
@TableName("love_value")
public class LoveValue implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long coupleId;
    private Long userId;

    /** 来源约定；NULL 表示非约定来源。配合 uk_love_value_plan_user(plan_id,user_id) 做幂等 */
    private Long planId;

    private Integer delta;
    private String reason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
