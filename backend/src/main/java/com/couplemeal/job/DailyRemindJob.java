package com.couplemeal.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.couplemeal.common.util.WorkdayUtil;
import com.couplemeal.domain.enums.NotifyType;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.Couple;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.entity.Notification;
import com.couplemeal.mapper.CoupleMapper;
import com.couplemeal.mapper.MealPlanMapper;
import com.couplemeal.mapper.NotificationMapper;
import com.couplemeal.service.NotifyService;
import com.couplemeal.service.impl.NotifyServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日提醒任务（见 50-通知/后端.md §4）。
 *
 * <p>cron 来自配置 {@code couple-meal.schedule.daily-remind-cron}（工作日 11:00）。
 * 动作：找出当天 {@code status=0 待认领} 的约定，给**带饭方**（非发起方）发一条 type=5 通知。
 *
 * <p>去重：每日提醒 planId 为 NULL，唯一索引 {@code uk_notification_plan_type_receiver} 对它不生效，
 * 必须显式查「该收件人今天是否已有 type=5」——条件恰好是
 * {@code idx_notification_receiver_type_time(receiver_id, type, create_time)} 的前缀，走索引。
 * 一句话：当天提醒过就跳过（PRD §12.6 只催一次）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyRemindJob {

    private final MealPlanMapper mealPlanMapper;
    private final CoupleMapper coupleMapper;
    private final NotificationMapper notificationMapper;
    private final NotifyService notifyService;

    @Scheduled(cron = "${couple-meal.schedule.daily-remind-cron}")
    public void run() {
        try {
            LocalDate today = LocalDate.now();
            // cron 已限定 MON-FRI，这里兜住手动/测试触发，节假日同样跳过
            if (!WorkdayUtil.isWorkday(today)) {
                return;
            }
            // 只读约定表：当天待认领。跨模块只读直查 Mapper，不改别人模块的状态
            List<MealPlan> pending = mealPlanMapper.selectList(new LambdaQueryWrapper<MealPlan>()
                    .select(MealPlan::getId, MealPlan::getCoupleId, MealPlan::getOrdererId)
                    .eq(MealPlan::getStatus, PlanStatus.WAIT_CLAIM.getCode())
                    .eq(MealPlan::getPlanDate, today));

            LocalDateTime dayStart = today.atStartOfDay();
            Map<Long, Long> remindedToday = new HashMap<>();   // receiverId -> 当天 type=5 条数，0 表示未提醒过
            int sent = 0;
            for (MealPlan plan : pending) {
                Long receiver = resolvePartner(plan.getCoupleId(), plan.getOrdererId());
                if (receiver == null) {
                    continue;
                }
                if (remindedToday.computeIfAbsent(receiver, r -> countDailyRemind(r, dayStart)) > 0) {
                    continue;                                  // 当天已催过，不再催
                }
                notifyService.push(plan.getCoupleId(), receiver, null, NotifyType.DAILY_REMIND,
                        NotifyServiceImpl.DAILY_REMIND_TITLE, NotifyServiceImpl.DAILY_REMIND_CONTENT);
                remindedToday.put(receiver, 1L);               // 同一轮内不再重复
                sent++;
            }
            if (sent > 0) {
                log.info("DailyRemindJob 发出 {} 条每日提醒", sent);
            }
        } catch (Exception e) {
            // 定时任务失败不能影响主流程，记录后跳过本轮
            log.error("DailyRemindJob 执行异常", e);
        }
    }

    /**
     * 当天已发给该收件人的每日提醒条数（0 表示没发过）。
     */
    private long countDailyRemind(Long receiverId, LocalDateTime dayStart) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getType, NotifyType.DAILY_REMIND.getCode())
                .ge(Notification::getCreateTime, dayStart));
    }

    /**
     * 取「另一半」= 带饭方。待认领时 carrierId 还没写入，只能由关系的 userA / userB 反推。
     */
    private Long resolvePartner(Long coupleId, Long ordererId) {
        Couple couple = coupleMapper.selectById(coupleId);
        if (couple == null || couple.getStatus() == null || couple.getStatus() != 1) {
            return null;                                       // 关系已解绑，不打扰
        }
        Long a = couple.getUserA();
        Long b = couple.getUserB();
        if (a == null || b == null) {
            return null;
        }
        return a.equals(ordererId) ? b : a;
    }
}
