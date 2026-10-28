package com.couplemeal.service;

import com.couplemeal.common.PageResult;
import com.couplemeal.domain.HistoryItem;
import com.couplemeal.domain.ReportVO;
import com.couplemeal.domain.WeekVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 历史 / 周历 / 报告（模块 70 + 90）。
 *
 * <p>三个能力都是 <b>meal_plan 及其子表的只读视图</b>，不产生任何写操作。
 * 跨模块只直查 Mapper（MealPlan / MealPlanItem / MealCheckin / MealCheckinImage），
 * 不注入其他 Service，避免循环依赖。
 *
 * <p>数据隔离：coupleId 只从 UserContext 取，所有查询必带 couple_id 条件。
 */
public interface HistoryService {

    /**
     * 历史列表：只返回 {@code plan_date < 今天} 的约定（今天的在今日页），包含 4/5/6 三种终态。
     *
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数，默认 20、上限 50
     * @param statuses 状态多选（0–6），空 / null 等同不筛
     * @param role     角色筛选：{@code orderer} 我点的 / {@code carrier} 我带的；空为不筛
     */
    PageResult<HistoryItem> history(int pageNo, int pageSize, List<Integer> statuses, String role);

    /**
     * 周历：固定 7 天（周一至周日），一次查询取全周后内存分组。
     *
     * @param startDate 周内任意日期（自动对齐到该周周一）；null 为本周一。超出 ±4 周返回空周，不报错
     */
    WeekVO week(LocalDate startDate);

    /**
     * 月报 / 年报。
     *
     * @param scope  {@code month} / {@code year}
     * @param anchor 月报 {@code 2026-09}、年报 {@code 2026}；
     *               null / 空时月报取上一自然月、年报取当年
     */
    ReportVO report(String scope, String anchor);
}
