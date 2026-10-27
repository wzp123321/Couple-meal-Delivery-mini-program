package com.couplemeal.common.util;

/**
 * 53bit 雪花 ID 生成器（见 docs/design/后端-00-架构.md §5）。
 *
 * <p>位宽：41 位毫秒时间 + 12 位序列 = 53 位，省略节点位（单实例部署）。
 * 选 53bit 是为了让 id 经接口返回小程序后，JS Number（2^53-1 精度）不丢位。
 * 自定义纪元 2026-01-01 00:00:00 (UTC+8)，可用约 69 年。
 */
public final class IdUtil {

    /** 自定义纪元 2026-01-01 00:00:00 (UTC+8) */
    private static final long EPOCH = 1767225600000L;
    private static final long SEQ_BITS = 12L;
    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;   // 4095

    private static long lastTime = -1L;
    private static long seq = 0L;

    private IdUtil() {
    }

    public static synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTime) {
            now = lastTime;              // 时钟回拨：沿用上次时间，不倒退
        }
        if (now == lastTime) {
            seq = (seq + 1) & MAX_SEQ;
            if (seq == 0) {              // 当毫秒序列耗尽，自旋到下一毫秒
                while ((now = System.currentTimeMillis()) <= lastTime) {
                    Thread.onSpinWait();
                }
            }
        } else {
            seq = 0L;
        }
        lastTime = now;
        return ((now - EPOCH) << SEQ_BITS) | seq;
    }
}
