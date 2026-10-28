package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 站内通知类型（notification.type）。
 * 同一约定同一类型对同一收件人只发一次（uk_notification_plan_type_receiver）。
 */
@Getter
public enum NotifyType {

    NEW_PLAN(1, "新约定"),
    CLAIMED(2, "被认领"),
    DELIVERED(3, "已送达"),
    COMPLETED(4, "已完成"),
    DAILY_REMIND(5, "每日提醒"),
    CHECKED_IN(6, "已打卡");

    private final int code;
    private final String desc;

    NotifyType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static NotifyType of(int code) {
        for (NotifyType e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的通知类型：" + code);
    }
}
