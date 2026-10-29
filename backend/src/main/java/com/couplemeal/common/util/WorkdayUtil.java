package com.couplemeal.common.util;

import com.couplemeal.domain.enums.MealType;
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
 *   <li>{@link #isTodayOrderClosed(LocalDateTime, MealType)}：工作日对应餐段的截止小时及之后，
 *       当日不可再发起该餐段约定（失败码 1010 TOO_LATE_TODAY）；非工作日返回 true（本身就不能约）。</li>
 * </ul>
 *
 * <p>调用方式：纯静态，Job / Service 直接 {@code WorkdayUtil.isWorkday(date)}，
 * 无需注入 Bean。截止小时由 {@code couple-meal.plan.cutoff.lunch}(默认 11) /
 * {@code couple-meal.plan.cutoff.dinner}(默认 18) 通过内部 Binder 组件启动时写入静态字段；
 * 未配置时回落到默认值，无需改动 application.yml。
 */
public final class WorkdayUtil {

    /** 午饭默认截止小时（11:00 后不可约当日午饭） */
    private static final int DEFAULT_LUNCH_CUTOFF_HOUR = 11;
    /** 晚饭默认截止小时（18:00 后不可约当日晚饭） */
    private static final int DEFAULT_DINNER_CUTOFF_HOUR = 18;

    private static volatile int lunchCutoffHour = DEFAULT_LUNCH_CUTOFF_HOUR;
    private static volatile int dinnerCutoffHour = DEFAULT_DINNER_CUTOFF_HOUR;

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
     * 当前时刻是否已过指定餐段的当日约定截止时间。
     * 非工作日恒为 true（本就不允许约，见错误码 1009）。
     */
    public static boolean isTodayOrderClosed(LocalDateTime now, MealType mealType) {
        return isTodayOrderClosed(now, cutoffHourFor(mealType));
    }

    private static int cutoffHourFor(MealType mealType) {
        return mealType == MealType.DINNER ? dinnerCutoffHour : lunchCutoffHour;
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

    /**
     * 配置绑定：把 yml 中的 couple-meal.plan.cutoff.lunch / .dinner 注入静态字段。
     * 独立组件，保证外层 WorkdayUtil 仍是纯静态工具类。
     */
    @Component
    static class CutoffHourBinder {

        CutoffHourBinder(
                @Value("${couple-meal.plan.cutoff.lunch:11}") int lunchCutoffHour,
                @Value("${couple-meal.plan.cutoff.dinner:18}") int dinnerCutoffHour) {
            WorkdayUtil.lunchCutoffHour = lunchCutoffHour;
            WorkdayUtil.dinnerCutoffHour = dinnerCutoffHour;
        }
    }
}
