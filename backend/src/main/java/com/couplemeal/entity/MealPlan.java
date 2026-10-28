package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 点餐约定（核心表），对应 meal_plan。
 * 字段与 db/schema.sql 逐字一致（下划线转驼峰）。
 */
@Data
@TableName("meal_plan")
public class MealPlan implements Serializable {

    /** 主键（53bit 雪花 ID，全局 id-type: ASSIGN_ID 生成） */
    private Long id;

    /** 关系隔离键，只从 UserContext 取 */
    private Long coupleId;

    /** 点餐方（想吃什么的人） */
    private Long ordererId;

    /** 带饭方（认领后 / 主动带时写入） */
    private Long carrierId;

    /** 1午饭 2晚饭，见 MealType */
    private Integer mealType;

    /** 0点餐 1主动带，见 PlanSource */
    private Integer source;

    /** 0待认领 1待接收 2备餐中 3已交付 4已完成 5已取消 6已过期，见 PlanStatus */
    private Integer status;

    /** 约定日期（工作日） */
    private LocalDate planDate;

    /** 口味/时间/忌口备注 ≤100字 */
    private String remark;

    /** 交付位置：前台/工位/门口等 */
    private String deliverLocation;

    /** 便签留言 ≤80字 */
    private String deliverNote;

    private LocalDateTime claimedTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime completedTime;
    private LocalDateTime cancelledTime;

    /** 乐观锁，防双端并发改状态（配合 OptimisticLockerInnerInterceptor） */
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
