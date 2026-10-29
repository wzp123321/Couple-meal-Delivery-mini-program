package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 石头剪刀布对局（rps_game）。
 *
 * <p>规则（见 openspec/changes/phase-2/specs/rps-negotiation）：
 *   - 一方针对 (couple_id, plan_date, meal_type) 发起，默认 best-of-1；
 *   - 平局重出：status 保持 0（待出拳），current_round 自增，直至分出胜负；
 *   - 胜者之外的一方（输家）为建议带饭方，仅在「采纳」时生成 source=1 约定。
 *
 * <p>status：0 进行中（待出拳）/ 1 已完成。winner_id 完成时写入。
 * plan_id 为采纳后生成的约定，未采纳为 null（幂等防重复生成）。
 */
@Data
@TableName("rps_game")
public class RpsGame implements Serializable {

    /** 主键（53bit 雪花 ID） */
    private Long id;

    /** 关系隔离键 */
    private Long coupleId;

    /** 约定日期（工作日） */
    private LocalDate planDate;

    /** 餐段：1午饭 2晚饭 */
    private Integer mealType;

    /** 发起方 user_id */
    private Long initiatorId;

    /** 0 进行中 / 1 已完成 */
    private Integer status;

    /** 胜方 user_id（完成时写入） */
    private Long winnerId;

    /** 采纳后生成的约定 id（未采纳为 null） */
    private Long planId;

    /** 当前回合号（平局重出时自增，从 1 起） */
    private Integer currentRound;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
