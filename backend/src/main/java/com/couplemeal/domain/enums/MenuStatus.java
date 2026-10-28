package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 菜单数据状态（menu_item.status）：软删除用，所有业务查询必须带 status = 1（见 00-全局约定.md §2.1）。
 */
@Getter
public enum MenuStatus {

    ABNORMAL(0, "异常"),
    NORMAL(1, "正常"),
    DELETED(2, "已删除");

    private final int code;
    private final String desc;

    MenuStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static MenuStatus of(int code) {
        for (MenuStatus e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的菜单状态：" + code);
    }

    /**
     * 是否为可查询的正常数据。
     */
    public boolean visible() {
        return this == NORMAL;
    }
}
