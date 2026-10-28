package com.couplemeal.domain.enums;

/**
 * 邀请码状态（couple_invite.status）。TINYINT 在 Java 侧用 Integer，
 * 本枚举仅作可读性常量，不直接做 MP 枚举映射。
 */
public enum InviteStatus {

    PENDING(0),
    USED(1),
    EXPIRED(2),
    VOID(3);

    private final int code;

    InviteStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
