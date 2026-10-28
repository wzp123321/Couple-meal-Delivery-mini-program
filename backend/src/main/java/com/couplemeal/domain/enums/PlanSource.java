package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 约定来源（meal_plan.source）。
 * 0 点餐：orderer 发起 → 待认领；1 主动带：carrier 发起 → 待接收。
 */
@Getter
public enum PlanSource {

    ORDER(0, "点餐"),
    BRING(1, "主动带");

    private final int code;
    private final String desc;

    PlanSource(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static PlanSource of(int code) {
        for (PlanSource e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的约定来源：" + code);
    }
}
