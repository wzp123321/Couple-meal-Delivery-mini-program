package com.couplemeal.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 53bit 雪花 ID 自测：验证生成值落在 2^53-1 以内（JS Number.MAX_SAFE_INTEGER），
 * 否则返回小程序会静默丢位。见 后端-00-架构.md §5。
 */
class IdUtilTest {

    private static final long MAX_SAFE = (1L << 53) - 1;

    @Test
    void nextIdFitsIn53Bits() {
        for (int i = 0; i < 100_000; i++) {
            long id = IdUtil.nextId();
            assertTrue(id > 0, "id 必须为正数");
            assertTrue(id <= MAX_SAFE, "id 超出 53bit 安全整数范围: " + id);
        }
    }
}
