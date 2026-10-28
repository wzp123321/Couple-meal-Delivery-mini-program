package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 取消日志，对应 cancel_log。
 * 一个约定只记一次（uk_cancel_log_plan）；取消不发爱心值，留痕便于复盘。
 */
@Data
@TableName("cancel_log")
public class CancelLog implements Serializable {

    /** 主键（53bit 雪花 ID） */
    private Long id;

    /** 关联约定 */
    private Long planId;

    /** 关系隔离键 */
    private Long coupleId;

    /** 谁取消的 */
    private Long operatorId;

    /** 取消原因，必填 */
    private String reason;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
