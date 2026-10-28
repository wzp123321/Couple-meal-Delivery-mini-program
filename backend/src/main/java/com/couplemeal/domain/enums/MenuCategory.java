package com.couplemeal.domain.enums;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import lombok.Getter;

/**
 * 菜品分类（menu_item.category）。
 */
@Getter
public enum MenuCategory {

    MEAT(1, "荤"),
    VEGGIE(2, "素"),
    SOUP(3, "汤"),
    STAPLE(4, "主食");

    private final int code;
    private final String desc;

    MenuCategory(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 取枚举，找不到抛 PARAM_INVALID。
     */
    public static MenuCategory of(int code) {
        for (MenuCategory e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知的菜品分类：" + code);
    }
}
