package com.couplemeal.service;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 爱心值：流水表（不是余额表），唯一写入入口。
 *
 * <p>幂等由 {@code uk_love_value_plan_user} 兜底：同一约定对同一人只可能加一次，
 * 并发点「完成」时后者抛 {@code DuplicateKeyException}，视为成功，不回滚。
 */
public interface LoveValueService {

    /**
     * 发放爱心值（+1，带饭方）。重复调用由唯一约束拒绝。
     *
     * @param coupleId 关系隔离键
     * @param userId   获得方（carrier）
     * @param planId   来源约定
     */
    void award(Long coupleId, Long userId, Long planId);

    /**
     * 统计：关系卡（{@code GET /api/couple/info}）与 {@code GET /api/love/summary} 共用。
     *
     * @param coupleId 关系隔离键
     * @param myUserId 当前登录人，用于拆分 mine / partner
     */
    Summary summary(Long coupleId, Long myUserId);

    /**
     * 爱心值汇总。
     */
    @Data
    class Summary {
        /** 双方合计 */
        private Long total;
        /** 我（当前登录人）的爱心值 */
        private Long mine;
        /** 对方的爱心值 */
        private Long partner;
        /** 最近流水，最多 10 条 */
        private List<Recent> recent;
    }

    /**
     * 最近一条流水。
     */
    @Data
    class Recent {
        private Long planId;
        private String reason;
        private LocalDateTime createTime;
    }
}
