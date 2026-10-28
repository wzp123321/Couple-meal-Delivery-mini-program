package com.couplemeal.job;

import com.couplemeal.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 已交付满 24 小时自动完成（见 {@code 20-约定/后端.md §4.1}）。
 *
 * <p>cron 来自配置 {@code couple-meal.schedule.auto-complete-cron}（每小时整点）。
 * 防止一方迟迟不点「已领取」把爱心值卡住，超时由系统照发。
 *
 * <p>本 Job 只负责扫描与汇总计数，状态改写与加分一律复用 {@code PlanService.autoComplete(planId)}，
 * 与手动「已领取」走同一条加分逻辑 —— Job 不直接写 SQL 改状态，
 * 否则会漏掉通知、爱心值和 version 自增（{@code 01-通用机制.md §4.3}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCompleteJob {

    /** 交付后自动完成的等待时长（小时） */
    private static final int AUTO_COMPLETE_HOURS = 24;
    /** 单次处理上限，防长事务锁表（{@code 01-通用机制.md §4.3}） */
    private static final int BATCH_LIMIT = 200;

    private final PlanService planService;

    @Scheduled(cron = "${couple-meal.schedule.auto-complete-cron}")
    public void run() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(AUTO_COMPLETE_HOURS);
        int total = 0;
        int handled = 0;
        int failed = 0;
        try {
            List<Long> planIds = planService.findAutoCompleteIds(deadline, BATCH_LIMIT);
            total = planIds.size();
            for (Long planId : planIds) {
                try {
                    planService.autoComplete(planId);
                    handled++;
                } catch (Exception e) {
                    // 单条失败不影响其余约定（例如用户刚好手点完成 -> 1004），记录后继续
                    failed++;
                    log.warn("AutoCompleteJob 处理失败 planId={}", planId, e);
                }
            }
        } catch (Exception e) {
            log.error("AutoCompleteJob 扫描异常", e);
        }
        if (total > 0 || failed > 0) {
            log.info("AutoCompleteJob 扫描 {} 条，处理 {} 条，失败 {} 条", total, handled, failed);
        }
    }
}
