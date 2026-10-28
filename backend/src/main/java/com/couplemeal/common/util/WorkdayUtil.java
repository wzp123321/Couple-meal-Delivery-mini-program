package com.couplemeal.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作日判定工具（见 20-约定/后端.md §3.1、后端-00-架构.md §4）。
 *
 * <p>规则：
 * <ul>
 *   <li>{@link #isWorkday(LocalDate)}：周一至周五为 true（不做法定节假日，MVP 取舍）；</li>
 *   <li>{@link #isTodayOrderClosed(LocalDateTime)}：工作日 {@code order-cutoff-hour} 点及之后，
 *       当日不可再发起约定（失败码 1010）；非工作日返回 true（本身就不能约，约不了当天）。</li>
 * </ul>
 *
 * <p>调用方式：纯静态，Job / Service 直接 {@code WorkdayUtil.isWorkday(date)}，
 * 无需注入 Bean。截止小时由 {@code couple-meal.plan.order-cutoff-hour}（默认 18）
 * 通过内部 Binder 组件启动时写入静态字段；若未被扫描到，回落到默认值。
 */
public final class WorkdayUtil {

    /** 默认截止小时，需与 application.yml couple-meal.plan.order-cutoff-hour 保持一致 */
    private static final int DEFAULT_CUTOFF_HOUR = 18;

    /** 当前生效的截止小时，由 CutoffHourBinder 启动时注入 */
    private static volatile int cutoffHour = DEFAULT_CUTOFF_HOUR;

    private WorkdayUtil() {
    }

    /**
     * 是否工作日：周一至周五 true，周六日 false（不判法定节假日）。
     */
    public static boolean isWorkday(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /**
     * 当前时刻是否已过当日约定截止时间（不能约当天）。
     * 非工作日恒为 true（本就不允许约，见错误码 1009）。
     */
    public static boolean isTodayOrderClosed(LocalDateTime now) {
        return isTodayOrderClosed(now, getCutoffHour());
    }

    /**
     * 同上，截止小时由调用方显式指定（便于测试 / 特殊场景）。
     */
    public static boolean isTodayOrderClosed(LocalDateTime now, int cutoffHour) {
        if (now == null) {
            return false;
        }
        LocalDate today = now.toLocalDate();
        if (!isWorkday(today)) {
            return true;
        }
        return now.getHour() >= cutoffHour;
    }

    public static int getCutoffHour() {
        return cutoffHour;
    }

    /**
     * 配置绑定：把 yml 中的 couple-meal.plan.order-cutoff-hour 注入静态字段。
     * 独立组件，保证外层 WorkdayUtil 仍是纯静态工具类。
     */
    @Component
    static class CutoffHourBinder {

        CutoffHourBinder(@Value("${couple-meal.plan.order-cutoff-hour:18}") int cutoffHour) {
            WorkdayUtil.cutoffHour = cutoffHour;
        }
    }
}
