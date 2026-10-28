package com.couplemeal.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 历史列表项 VO（模块 70，见 docs/design/70-历史/后端.md §2）。
 *
 * <p>只带列表需要的精简字段；详情页才返回 actions / 备注等，历史项不做操作。
 * 菜名取 {@code meal_plan_item.name} 快照（菜单改名不影响历史，见 D2）。
 */
@Data
public class HistoryItem implements Serializable {

    /** 约定 id */
    private Long id;

    /** 约定日期 */
    private LocalDate planDate;

    /** 所属月份 {@code yyyy-MM}，前端据此按月分组 */
    private String month;

    /** 状态：0–6，见 PlanStatus（历史里主要是 4/5/6 三种终态） */
    private Integer status;

    /** 我的角色：{@code orderer} 我点的 / {@code carrier} 我带的 */
    private String role;

    /** 菜品名快照列表，按 seq 升序 */
    private List<String> items;

    /** 对方昵称，用于「和小满一起吃的饭」这类文案 */
    private String partnerNickname;

    /** 打卡评分，未打卡为 null */
    private Integer checkinRating;

    /** 是否已打卡 */
    @JsonProperty("hasCheckin")
    private Boolean hasCheckin;

    /** 打卡首图缩略图（审核通过且 seq 最小的那张），无图为 null */
    private String thumbUrl;
}
