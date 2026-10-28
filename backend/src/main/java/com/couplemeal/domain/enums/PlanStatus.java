package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 约定状态（meal_plan.status）。
 * 见 db/schema.sql meal_plan 字段 COMMENT。
 */
@Getter
public enum PlanStatus {

    WAIT_CLAIM(0, "待认领"),
    WAIT_ACCEPT(1, "待接收"),
    COOKING(2, "备餐中"),
    DELIVERED(3, "已交付"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    EXPIRED(6, "已过期");

    private final int code;
    private final String desc;

    PlanStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static PlanStatus of(int code) {
        for (PlanStatus e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的约定状态：" + code);
    }
}
