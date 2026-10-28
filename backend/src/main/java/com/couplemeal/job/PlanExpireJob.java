package com.couplemeal.job;

import com.couplemeal.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 次日 02:30 把「未交付」的昨日约定转为已过期（见 {@code 01-通用机制.md §4.2}，D11）。
 *
 * <p>cron 来自配置 {@code couple-meal.schedule.expire-cron}（每天跑，含周末，收尾周五遗留）。
 * 只处理 {@code status ∈ (0,1,2)}：<b>已交付（status=3）不参与过期</b>，它走 24h 自动完成。
 * 过期不发爱心值，归档后详情页只读（PRD §12.4 已取消 / 已过期不可打卡）。
 *
 * <p>状态改写复用 {@code PlanService.expire(planId)}，本 Job 只负责扫描与汇总计数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanExpireJob {

    /** 单次处理上限，防长事务锁表（{@code 01-通用机制.md §4.3}） */
    private static final int BATCH_LIMIT = 200;

    private final PlanService planService;

    @Scheduled(cron = "${couple-meal.schedule.expire-cron}")
    public void run() {
        LocalDate deadline = LocalDate.now().minusDays(1);
        int total = 0;
        int handled = 0;
        int failed = 0;
        try {
            List<Long> planIds = planService.findExpireIds(deadline, BATCH_LIMIT);
            total = planIds.size();
            for (Long planId : planIds) {
                try {
                    planService.expire(planId);
                    handled++;
                } catch (Exception e) {
                    // 单条失败不影响其余约定（例如刚被认领导致 version 变化 -> 1004），记录后继续
                    failed++;
                    log.warn("PlanExpireJob 处理失败 planId={}", planId, e);
                }
            }
        } catch (Exception e) {
            log.error("PlanExpireJob 扫描异常", e);
        }
        if (total > 0 || failed > 0) {
            log.info("PlanExpireJob 扫描 {} 条，处理 {} 条，失败 {} 条", total, handled, failed);
        }
    }
}
