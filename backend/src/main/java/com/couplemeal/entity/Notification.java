package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知（红点来源），对应 notification。
 * plan_id + type + receiver_id 唯一，同约定同类型只发一次（plan_id 为 NULL 的每日提醒不参与）。
 */
@Data
@TableName("notification")
public class Notification implements Serializable {

    /** 主键（53bit 雪花 ID） */
    private Long id;

    /** 关系隔离键 */
    private Long coupleId;

    /** 收件人 */
    private Long receiverId;

    /** 关联约定；每日提醒为空 */
    private Long planId;

    /** 1新约定 2被认领 3已送达 4已完成 5每日提醒 6已打卡，见 NotifyType */
    private Integer type;

    private String title;
    private String content;

    /** 0未读 1已读 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
