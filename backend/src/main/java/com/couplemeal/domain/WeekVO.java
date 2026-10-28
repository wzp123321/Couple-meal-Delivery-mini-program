package com.couplemeal.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 周历 VO（模块 90，见 docs/design/90-周历/后端.md §2）。
 *
 * <p>固定 7 天，无约定的日子也占位（{@code plans: []}），前端才能画满 7 行。
 * 周末 {@code isWorkday=false}，前端据此显示「休息」。
 */
@Data
public class WeekVO implements Serializable {

    /** 本周周一（含） */
    private LocalDate weekStart;

    /** 固定 7 天，从周一到周日 */
    private List<Day> days;

    /** 周历中的一天 */
    @Data
    public static class Day implements Serializable {

        private LocalDate date;

        /** 1–7，周一为 1（ISO） */
        private Integer weekday;

        /** 周一至周五为 true；周末为 false（前端显示「休息」） */
        @JsonProperty("isWorkday")
        private Boolean isWorkday;

        /** 是否为今天（整行高亮） */
        @JsonProperty("isToday")
        private Boolean isToday;

        /** 当天的约定列表，没有则为空数组 */
        private List<Plan> plans;
    }

    /** 周历中的约定卡片（精简版，不含操作按钮） */
    @Data
    public static class Plan implements Serializable {

        private Long id;

        /** 状态：0–6 */
        private Integer status;

        /** 我的角色：orderer / carrier */
        private String role;

        /** 菜品名快照列表 */
        private List<String> items;
    }
}
