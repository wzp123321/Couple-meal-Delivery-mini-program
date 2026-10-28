package com.couplemeal.job;

import com.couplemeal.mapper.CoupleInviteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 邀请码过期任务（见 后端.md §4 / 01-通用机制.md §4）。
 *
 * <p>cron 来自配置 couple-meal.schedule.invite-expire-cron（每小时整点，见 application.yml）。
 * 动作：status=0 且 expire_time < now → status=2（已过期）。单条 UPDATE 原子处理，无需分批。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteExpireJob {

    private final CoupleInviteMapper coupleInviteMapper;

    @Scheduled(cron = "${couple-meal.schedule.invite-expire-cron}")
    public void run() {
        try {
            int n = coupleInviteMapper.expireOverdue(LocalDateTime.now());
            if (n > 0) {
                log.info("InviteExpireJob 处理 {} 条过期邀请码", n);
            }
        } catch (Exception e) {
            // 定时任务失败不能影响主流程，记录后跳过本轮
            log.error("InviteExpireJob 执行异常", e);
        }
    }
}
