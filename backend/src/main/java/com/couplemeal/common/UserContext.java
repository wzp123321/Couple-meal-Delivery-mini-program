package com.couplemeal.common;

/**
 * 请求上下文（ThreadLocal）：存放当前登录用户身份。
 *
 * <p>数据隔离铁律（00-全局约定.md §3）：coupleId 只从这里取，任何接口入参都禁止接收 coupleId / status 等越权字段。
 * 由 AuthInterceptor 在请求开始时写入，afterCompletion 时清理。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> COUPLE_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, Long coupleId) {
        USER_ID.set(userId);
        COUPLE_ID.set(coupleId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 当前用户所属关系 id；未绑定关系时为 null。
     */
    public static Long getCoupleId() {
        return COUPLE_ID.get();
    }

    /**
     * 取 coupleId，未绑定则抛 NO_COUPLE（业务接口几乎都要求已绑定）。
     */
    public static Long requireCoupleId() {
        Long cid = COUPLE_ID.get();
        if (cid == null) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        return cid;
    }

    public static void clear() {
        USER_ID.remove();
        COUPLE_ID.remove();
    }
}
