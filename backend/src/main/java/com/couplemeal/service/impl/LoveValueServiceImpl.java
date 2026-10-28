package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.entity.LoveValue;
import com.couplemeal.mapper.LoveValueMapper;
import com.couplemeal.service.LoveValueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 爱心值服务实现（流水表，不是余额表；见 40-爱心值/后端.md）。
 *
 * <p>三条铁律：
 *  1. 写入只走 {@link #award}，且**不做先查再插** —— 并发下必然漏判，交给唯一索引兜底（D7）。
 *  2. 只 catch {@link DuplicateKeyException}，确保真故障时异常照常外抛（不静默丢分）。
 *  3. 统计每次 SUM(delta)，不做冗余计数列（两人一年几百条，见 00-全局约定.md §6）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoveValueServiceImpl implements LoveValueService {

    /** 完成一次带饭的加分理由 */
    private static final String REASON_COMPLETE = "带饭完成";

    /** 每次完成 +1 分，产品不做权重区分 */
    private static final int DELTA_PER_PLAN = 1;

    /** 最近流水返回条数上限 */
    private static final int RECENT_LIMIT = 10;

    private final LoveValueMapper loveValueMapper;

    // ===================== 写入（唯一入口） =====================

    @Override
    @Transactional
    public void award(Long coupleId, Long userId, Long planId) {
        LoveValue lv = new LoveValue();
        lv.setId(IdUtil.nextId());
        lv.setCoupleId(coupleId);
        lv.setUserId(userId);
        lv.setPlanId(planId);
        lv.setDelta(DELTA_PER_PLAN);
        lv.setReason(REASON_COMPLETE);
        try {
            loveValueMapper.insert(lv);
        } catch (DuplicateKeyException e) {
            // uk_love_value_plan_user 拦截 = 该约定已给这个人加过分，幂等成功
            log.debug("爱心值重复发放已忽略 coupleId={} userId={} planId={}", coupleId, userId, planId);
        }
    }

    // ===================== 统计 =====================

    /**
     * 汇总：{@code GET /api/love/summary} 与关系卡 {@code GET /api/couple/info} 共用。
     *
     * <p>一次 GROUP BY 拿到双方各自累计（走 idx_love_value_couple_user）：
     * {@code SELECT user_id, SUM(delta) FROM love_value WHERE couple_id=? GROUP BY user_id}。
     * {@code partner = total - mine} 是刻意为之：即使流水里出现第三人（脏数据），
     * 也保证 {@code mine + partner == total} 这条验收恒成立。
     */
    @Override
    public Summary summary(Long coupleId, Long myUserId) {
        long total = 0L;
        long mine = 0L;
        // 别名不带下划线，避免 SQL 列的 Map key 受驼峰配置影响
        List<Map<String, Object>> rows = loveValueMapper.selectMaps(new QueryWrapper<LoveValue>()
                .select("user_id AS uid", "SUM(delta) AS valueSum")
                .eq("couple_id", coupleId)
                .groupBy("user_id"));
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                long sum = toLong(row.get("valueSum"));
                total += sum;
                if (myUserId != null && myUserId.equals(toLong(row.get("uid")))) {
                    mine = sum;
                }
            }
        }

        Summary summary = new Summary();
        summary.setTotal(total);
        summary.setMine(mine);
        summary.setPartner(total - mine);
        summary.setRecent(listRecent(coupleId));
        return summary;
    }

    /**
     * 最近 {@value #RECENT_LIMIT} 条流水（按时间倒序）。
     */
    private List<Recent> listRecent(Long coupleId) {
        List<LoveValue> latest = loveValueMapper.selectList(new LambdaQueryWrapper<LoveValue>()
                .eq(LoveValue::getCoupleId, coupleId)
                .orderByDesc(LoveValue::getCreateTime)
                .last("LIMIT " + RECENT_LIMIT));
        return latest.stream().map(lv -> {
            Recent r = new Recent();
            r.setPlanId(lv.getPlanId());
            r.setReason(lv.getReason());
            r.setCreateTime(lv.getCreateTime());
            return r;
        }).toList();
    }

    /**
     * SUM 在 MySQL 上返回 DECIMAL（驱动映射 BigDecimal），统一转 long；空值当 0。
     */
    private static long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
