package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 忌口等级（menu_item.avoid_level）：只提醒不硬拦（见 PRD Q2）。
 */
@Getter
public enum AvoidLevel {

    NONE(0, "无"),
    LESS(1, "少吃"),
    NO(2, "不吃"),
    ALLERGY(3, "过敏");

    private final int code;
    private final String desc;

    AvoidLevel(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static AvoidLevel of(int code) {
        for (AvoidLevel e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的忌口等级：" + code);
    }

    /**
     * 是否需要在发起约定时给前端返回强提醒（≥2 命中，只提醒不拦截）。
     */
    public boolean needWarn() {
        return this.code >= NO.code;
    }
}
