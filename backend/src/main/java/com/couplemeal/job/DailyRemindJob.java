package com.couplemeal.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.couplemeal.common.util.WorkdayUtil;
import com.couplemeal.domain.enums.MealType;
import com.couplemeal.domain.enums.NotifyType;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.Couple;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.mapper.CoupleMapper;
import com.couplemeal.mapper.MealPlanMapper;
import com.couplemeal.service.NotifyService;
import com.couplemeal.service.impl.NotifyServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 每日提醒任务（见 50-通知/后端.md §4）。
 *
 * <p>按餐段拆成两个入口：午饭 {@code couple-meal.schedule.daily-remind-cron}（工作日 11:00）、
 * 晚饭 {@code couple-meal.schedule.daily-remind-dinner-cron}（工作日 17:00），各自只处理对应
 * {@code meal_type} 的待认领约定，给**带饭方**（非发起方）发一条 type=5 通知。
 *
 * <p>开关：{@code couple.remind_enabled = 0} 的关系整条跳过（设置页关掉开关就不打扰）。
 * 关系按 coupleId 批量查一次，避免逐条回表。
 *
 * <p>去重：通知携带 {@code plan_id}，交给唯一索引 {@code uk_notification_plan_type_receiver}
 * (plan_id, type, receiver_id) 兜底——同一约定同一收件人只留一条，午饭/晚饭互不干扰；
 * 并发下也不漏判（{@code NotifyService.push} 内部吞掉唯一键冲突）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyRemindJob {

    private final MealPlanMapper mealPlanMapper;
    private final CoupleMapper coupleMapper;
    private final NotifyService notifyService;

    /**
     * 午饭提醒（工作日 11:00）。
     */
    @Scheduled(cron = "${couple-meal.schedule.daily-remind-cron}")
    public void runLunch() {
        runFor(MealType.LUNCH);
    }

    /**
     * 晚饭提醒（工作日 17:00，晚饭 18:00 截止前留一小时）。
     */
    @Scheduled(cron = "${couple-meal.schedule.daily-remind-dinner-cron}")
    public void runDinner() {
        runFor(MealType.DINNER);
    }

    /**
     * 按餐段扫当天待认领约定并提醒。
     */
    private void runFor(MealType mealType) {
        try {
            LocalDate today = LocalDate.now();
            // cron 已限定 MON-FRI，这里兜住手动/测试触发，节假日同样跳过
            if (!WorkdayUtil.isWorkday(today)) {
                return;
            }
            // 只读约定表：当天 + 对应餐段的待认领。跨模块只读直查 Mapper，不改别人模块的状态
            List<MealPlan> pending = mealPlanMapper.selectList(new LambdaQueryWrapper<MealPlan>()
                    .select(MealPlan::getId, MealPlan::getCoupleId, MealPlan::getOrdererId)
                    .eq(MealPlan::getStatus, PlanStatus.WAIT_CLAIM.getCode())
                    .eq(MealPlan::getMealType, mealType.getCode())
                    .eq(MealPlan::getPlanDate, today));
            if (pending.isEmpty()) {
                return;
            }

            List<Long> coupleIds = pending.stream()
                    .map(MealPlan::getCoupleId)
                    .distinct()
                    .collect(Collectors.toList());
            // 一次批量取关系，判开关 + 反推带饭方，避免 N+1
            Map<Long, Couple> coupleMap = coupleMapper.selectList(new LambdaQueryWrapper<Couple>()
                            .select(Couple::getId, Couple::getUserA, Couple::getUserB,
                                    Couple::getRemindEnabled, Couple::getStatus)
                            .in(Couple::getId, coupleIds))
                    .stream()
                    .collect(Collectors.toMap(Couple::getId, Function.identity(), (x, y) -> x));

            int sent = 0;
            for (MealPlan plan : pending) {
                Couple couple = coupleMap.get(plan.getCoupleId());
                Long receiver = resolvePartner(couple, plan.getOrdererId());
                if (receiver == null) {
                    continue;                                  // 关系无效或已解绑，不打扰
                }
                notifyService.push(plan.getCoupleId(), receiver, plan.getId(), NotifyType.DAILY_REMIND,
                        NotifyServiceImpl.dailyRemindTitle(mealType),
                        NotifyServiceImpl.dailyRemindContent(mealType));
                sent++;
            }
            if (sent > 0) {
                log.info("DailyRemindJob[{}] 处理 {} 条待认领约定", mealType.getDesc(), sent);
            }
        } catch (Exception e) {
            // 定时任务失败不能影响主流程，记录后跳过本轮
            log.error("DailyRemindJob 执行异常 mealType={}", mealType, e);
        }
    }

    /**
     * 取「另一半」= 带饭方。待认领时 carrierId 还没写入，只能由关系的 userA / userB 反推。
     *
     * @return 收件人；关系无效 / 已解绑 / 关闭了每日提醒时返回 null（整条跳过）
     */
    private Long resolvePartner(Couple couple, Long ordererId) {
        if (couple == null || couple.getStatus() == null || couple.getStatus() != 1) {
            return null;
        }
        if (Boolean.FALSE.equals(couple.getRemindEnabled())) {
            return null;                                       // 用户关了每日提醒
        }
        Long a = couple.getUserA();
        Long b = couple.getUserB();
        if (a == null || b == null) {
            return null;
        }
        return a.equals(ordererId) ? b : a;
    }
}
