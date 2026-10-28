package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 餐次（meal_plan.meal_type）：1 午饭 2 晚饭（MVP 仅 1）。
 */
@Getter
public enum MealType {

    LUNCH(1, "午饭"),
    DINNER(2, "晚饭");

    private final int code;
    private final String desc;

    MealType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static MealType of(int code) {
        for (MealType e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的餐次：" + code);
    }
}
