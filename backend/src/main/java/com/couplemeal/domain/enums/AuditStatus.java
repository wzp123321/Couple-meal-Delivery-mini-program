package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 图片审核状态：meal_checkin.audit_status（汇总态）与 meal_checkin_image.audit_status（单张）共用。
 */
@Getter
public enum AuditStatus {

    AUDITING(0, "审核中"),
    PASSED(1, "通过"),
    BLOCKED(2, "屏蔽");

    private final int code;
    private final String desc;

    AuditStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static AuditStatus of(int code) {
        for (AuditStatus e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的审核状态：" + code);
    }
}
