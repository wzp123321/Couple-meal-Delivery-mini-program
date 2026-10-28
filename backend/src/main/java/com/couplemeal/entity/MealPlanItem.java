package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 约定菜品明细（菜名快照），对应 meal_plan_item。
 * 菜单改名/删除不影响历史约定（D2）。
 */
@Data
@TableName("meal_plan_item")
public class MealPlanItem implements Serializable {

    /** 主键（53bit 雪花 ID） */
    private Long id;

    /** 所属约定 */
    private Long planId;

    /** 来源菜品，手输菜名为空 */
    private Long menuItemId;

    /** 菜名快照 */
    private String name;

    /** 排序，1–10 道 */
    private Integer seq;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
