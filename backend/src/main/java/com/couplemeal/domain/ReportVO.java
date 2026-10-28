package com.couplemeal.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 月报 / 年报 VO（模块 70，见 docs/design/70-历史/后端.md §3.4）。
 *
 * <p>统计口径统一为「已完成」（{@code status=4}）：取消与过期不计入次数，
 * 与爱心值「只有完成才加分」的口径一致。
 * 空期间不造数据：{@code planCount=0} 时返回空报告，前端整卡不渲染。
 */
@Data
public class ReportVO implements Serializable {

    /** month / year */
    private String scope;

    /** 月报 {@code 2026-09}；年报 {@code 2026} */
    private String anchor;

    private LocalDate startDate;
    private LocalDate endDate;

    /** 期间内完成的约定数 */
    private Integer planCount;

    /** 双方各自带了几次 */
    private CarrierCount carrierCount = new CarrierCount();

    /** 出现最多的 3 道菜 */
    private List<DishCount> topDishes = new ArrayList<>();

    /** 有打卡的天数（同一天多次只算一天） */
    private Integer checkinDays;

    /** 期间爱心值增量（按 love_value.create_time 落在区间内统计） */
    private Integer loveValueDelta;

    /** 最长连续带饭天数，仅年报；月报为 null。空期间为 null */
    private Integer longestStreak;

    /** 对方昵称，供前端拼「你带了 8 次，TA 带了 6 次」 */
    private String partnerNickname;

    /** 谁带得多 */
    @Data
    public static class CarrierCount implements Serializable {

        /** 我带了几次 */
        private Integer me;

        /** 对方带了几次 */
        private Integer partner;
    }

    /** 最爱菜 */
    @Data
    public static class DishCount implements Serializable {

        private String name;
        private Integer count;
    }
}
