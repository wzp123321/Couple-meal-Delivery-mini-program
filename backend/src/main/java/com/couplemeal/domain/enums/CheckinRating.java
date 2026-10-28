package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 打卡评分（meal_checkin.rating）。
 */
@Getter
public enum CheckinRating {

    SO_SO(1, "还差点意思"),
    GOOD(2, "好吃"),
    GREAT(3, "香");

    private final int code;
    private final String desc;

    CheckinRating(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static CheckinRating of(int code) {
        for (CheckinRating e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的打卡评分：" + code);
    }
}
