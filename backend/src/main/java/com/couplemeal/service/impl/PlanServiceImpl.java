package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.common.util.WorkdayUtil;
import com.couplemeal.domain.PlanAction;
import com.couplemeal.domain.PlanAction.Role;
import com.couplemeal.domain.enums.AvoidLevel;
import com.couplemeal.domain.enums.MealType;
import com.couplemeal.domain.enums.NotifyType;
import com.couplemeal.domain.enums.PlanSource;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.CancelLog;
import com.couplemeal.entity.Couple;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.entity.MealPlanItem;
import com.couplemeal.mapper.CancelLogMapper;
import com.couplemeal.mapper.CoupleMapper;
import com.couplemeal.mapper.MealPlanItemMapper;
import com.couplemeal.mapper.MealPlanMapper;
import com.couplemeal.service.LoveValueService;
import com.couplemeal.service.MenuService;
import com.couplemeal.service.NotifyService;
import com.couplemeal.service.PlanService;
import com.couplemeal.service.PlanStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 约定服务实现（核心模块，见 {@code 20-约定/后端.md}）。
 *
 * <p>三条铁律落实处：
 * <ol>
 *   <li><b>状态只经状态机</b>：所有转移先 {@code assertCan} 再条件 UPDATE，Controller 传不进 status（D8/D9）；</li>
 *   <li><b>先改状态后加副作用</b>：通知与爱心值一律放在状态写成功之后，同事务提交；</li>
 *   <li><b>幂等交给数据库</b>：爱心值 / cancel_log 的唯一索引冲突按业务语义处理，
 *       不靠"先查再插"（D7）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    /** 一次约定的菜品数量下限 / 上限（上限同时受配置项约束） */
    private static final int MIN_ITEMS = 1;
    private static final int ITEM_NAME_MAX = 20;
    private static final int REMARK_MAX = 100;
    private static final int DELIVER_NOTE_MAX = 80;
    private static final int CANCEL_REASON_MAX = 100;
    /** 婉拒未填原因时的默认文案（PRD §12.11：语气要比"取消"柔和） */
    private static final String REJECT_DEFAULT_REASON = "婉拒";

    private final MealPlanMapper planMapper;
    private final MealPlanItemMapper planItemMapper;
    private final CancelLogMapper cancelLogMapper;
    private final CoupleMapper coupleMapper;
    private final PlanStateMachine stateMachine;
    private final NotifyService notifyService;
    private final LoveValueService loveValueService;
    private final MenuService menuService;

    @Value("${couple-meal.plan.max-items:10}")
    private int maxItems;

    @Value("${couple-meal.plan.max-date-ahead-days:7}")
    private int maxDateAheadDays;

    // ===================== 发起 =====================

    @Override
    @Transactional
    public CreateResult create(int mealType, int source, LocalDate planDate, List<ItemInput> items, String remark) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        MealType.of(mealType);                    // 餐次非法 -> PARAM_INVALID
        PlanSource planSource = PlanSource.of(source);

        // 校验顺序按「先便宜后贵」：日期 -> 菜品 -> 备注
        if (planDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "约定日期不能为空");
        }
        if (!WorkdayUtil.isWorkday(planDate)) {
            throw new BizException(ErrorCode.WEEKEND_NOT_ALLOWED);
        }
        LocalDate today = LocalDate.now();
        if (planDate.isBefore(today) || planDate.isAfter(today.plusDays(maxDateAheadDays))) {
            throw new BizException(ErrorCode.DATE_OUT_OF_RANGE);
        }
        if (planDate.equals(today) && WorkdayUtil.isTodayOrderClosed(LocalDateTime.now())) {
            throw new BizException(ErrorCode.TOO_LATE_TODAY);
        }
        if (items == null || items.size() < MIN_ITEMS || items.size() > maxItems) {
            throw new BizException(ErrorCode.ITEMS_TOO_MANY, "请选择 " + MIN_ITEMS + "-" + maxItems + " 道菜");
        }
        List<String> names = new ArrayList<>(items.size());
        for (ItemInput input : items) {
            String name = input == null ? null : trimToNull(input.name);
            if (name == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜名不能为空");
            }
            if (name.length() > ITEM_NAME_MAX) {
                throw new BizException(ErrorCode.MENU_NAME_TOO_LONG);
            }
            names.add(name);
        }
        String finalRemark = trimToNull(remark);
        if (finalRemark != null && finalRemark.length() > REMARK_MAX) {
            throw new BizException(ErrorCode.REMARK_TOO_LONG);
        }

        Long partnerId = partnerOf(coupleId, me);
        MealPlan plan = new MealPlan();
        plan.setId(IdUtil.nextId());
        plan.setCoupleId(coupleId);
        plan.setMealType(mealType);
        plan.setSource(planSource.getCode());
        plan.setPlanDate(planDate);
        plan.setRemark(finalRemark);
        if (planSource == PlanSource.ORDER) {
            // 点餐：我想吃，等 TA 来带
            plan.setOrdererId(me);
            plan.setStatus(PlanStatus.WAIT_CLAIM.getCode());
        } else {
            // 主动带：我带饭，等 TA 点头（防一方单方面决定）
            plan.setOrdererId(partnerId);
            plan.setCarrierId(me);
            plan.setStatus(PlanStatus.WAIT_ACCEPT.getCode());
        }
        planMapper.insert(plan);

        // 菜名快照：菜单改名/删除不影响历史（D2）
        for (int i = 0; i < items.size(); i++) {
            MealPlanItem item = new MealPlanItem();
            item.setId(IdUtil.nextId());
            item.setPlanId(plan.getId());
            item.setMenuItemId(items.get(i).menuItemId);
            item.setName(names.get(i));
            item.setSeq(i);
            planItemMapper.insert(item);
        }

        CreateResult result = new CreateResult();
        result.planId = plan.getId();
        result.warnings = buildWarnings(coupleId, names);

        String dishes = String.join("、", names);
        if (planSource == PlanSource.ORDER) {
            notifyService.push(coupleId, partnerId, plan.getId(), NotifyType.NEW_PLAN,
                    "TA 想吃你做的饭", dishes);
        } else {
            notifyService.push(coupleId, partnerId, plan.getId(), NotifyType.NEW_PLAN,
                    "TA 要给你带饭啦", dishes);
        }
        return result;
    }

    // ===================== 读 =====================

    @Override
    public PlanDetail detail(Long planId) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        List<MealPlanItem> planItems = planItemMapper.selectList(new LambdaQueryWrapper<MealPlanItem>()
                .eq(MealPlanItem::getPlanId, planId)
                .orderByAsc(MealPlanItem::getSeq));

        PlanDetail detail = new PlanDetail();
        detail.id = plan.getId();
        detail.mealType = plan.getMealType();
        detail.source = plan.getSource();
        detail.status = plan.getStatus();
        detail.statusDesc = PlanStatus.of(plan.getStatus()).getDesc();
        detail.planDate = plan.getPlanDate();
        detail.remark = plan.getRemark();
        detail.deliverLocation = plan.getDeliverLocation();
        detail.deliverNote = plan.getDeliverNote();
        detail.ordererId = plan.getOrdererId();
        detail.carrierId = plan.getCarrierId();
        detail.claimedTime = plan.getClaimedTime();
        detail.deliveredTime = plan.getDeliveredTime();
        detail.completedTime = plan.getCompletedTime();
        detail.cancelledTime = plan.getCancelledTime();
        Role role = stateMachine.roleOf(plan, me);
        detail.iAmOrderer = role == Role.ORDERER;
        detail.iAmCarrier = role == Role.CARRIER;
        detail.items = new ArrayList<>();
        for (MealPlanItem item : planItems) {
            PlanItemVo vo = new PlanItemVo();
            vo.menuItemId = item.getMenuItemId();
            vo.name = item.getName();
            vo.seq = item.getSeq();
            detail.items.add(vo);
        }
        // actions[] 由状态机按 (status, 我的角色) 算，前端不自己推
        detail.actions = stateMachine.availableActions(plan, me);
        return detail;
    }

    @Override
    public List<PlanBrief> today() {
        Long coupleId = UserContext.requireCoupleId();
        List<MealPlan> plans = planMapper.selectList(new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getCoupleId, coupleId)
                .eq(MealPlan::getPlanDate, LocalDate.now())
                .orderByAsc(MealPlan::getMealType)
                .orderByAsc(MealPlan::getId));
        if (plans.isEmpty()) {
            return List.of();
        }
        List<Long> planIds = plans.stream().map(MealPlan::getId).toList();
        List<MealPlanItem> planItems = planItemMapper.selectList(new LambdaQueryWrapper<MealPlanItem>()
                .in(MealPlanItem::getPlanId, planIds)
                .orderByAsc(MealPlanItem::getSeq));
        Map<Long, List<String>> namesByPlan = new HashMap<>();
        for (MealPlanItem item : planItems) {
            namesByPlan.computeIfAbsent(item.getPlanId(), key -> new ArrayList<>()).add(item.getName());
        }
        List<PlanBrief> briefs = new ArrayList<>(plans.size());
        for (MealPlan plan : plans) {
            PlanBrief brief = new PlanBrief();
            brief.id = plan.getId();
            brief.mealType = plan.getMealType();
            brief.source = plan.getSource();
            brief.status = plan.getStatus();
            brief.statusDesc = PlanStatus.of(plan.getStatus()).getDesc();
            brief.planDate = plan.getPlanDate();
            brief.remark = plan.getRemark();
            brief.ordererId = plan.getOrdererId();
            brief.carrierId = plan.getCarrierId();
            brief.deliveredTime = plan.getDeliveredTime();
            brief.itemNames = namesByPlan.getOrDefault(plan.getId(), List.of());
            briefs.add(brief);
        }
        return briefs;
    }

    // ===================== 认领 / 撤回 =====================

    @Override
    @Transactional
    public void claim(Long planId) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        LocalDateTime now = LocalDateTime.now();
        // 点餐方自己点「我来带」-> 1005；两人同时认领时后到的 0 行 -> 1004
        stateMachine.transit(PlanAction.CLAIM, plan, me, wrapper -> wrapper
                .set(MealPlan::getCarrierId, me)
                .set(MealPlan::getClaimedTime, now));
        notifyService.push(plan.getCoupleId(), plan.getOrdererId(), planId, NotifyType.CLAIMED,
                "有人来带饭啦～", "TA 接下了你的约定，等着开饭吧");
    }

    @Override
    @Transactional
    public void withdraw(Long planId) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        // 退回待认领：不写 cancel_log、不发爱心值，与取消是两回事
        stateMachine.transit(PlanAction.WITHDRAW, plan, me, wrapper -> {
        });
    }

    // ===================== 确认 / 婉拒 =====================

    @Override
    @Transactional
    public void confirm(Long planId) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        stateMachine.transit(PlanAction.CONFIRM, plan, me, wrapper -> {
        });
        notifyService.push(plan.getCoupleId(), plan.getCarrierId(), planId, NotifyType.CLAIMED,
                "TA 答应啦～", "对方确认接收，可以开始做啦");
    }

    @Override
    @Transactional
    public void reject(Long planId, String reason) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        // 婉拒即取消：写 cancel_log 便于复盘，不发爱心值
        String finalReason = trimToNull(reason) == null ? REJECT_DEFAULT_REASON : trimToNull(reason);
        doCancel(plan, me, finalReason, PlanAction.REJECT);
    }

    // ===================== 送达 =====================

    @Override
    @Transactional
    public void deliver(Long planId, String deliverLocation, String deliverNote) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        String location = trimToNull(deliverLocation);
        String note = trimToNull(deliverNote);
        if (note != null && note.length() > DELIVER_NOTE_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "便签留言不能超过 " + DELIVER_NOTE_MAX + " 个字");
        }
        LocalDateTime now = LocalDateTime.now();
        stateMachine.transit(PlanAction.DELIVER, plan, me, wrapper -> wrapper
                .set(MealPlan::getDeliveredTime, now)
                .set(MealPlan::getDeliverLocation, location)
                .set(MealPlan::getDeliverNote, note));
        notifyService.push(plan.getCoupleId(), plan.getOrdererId(), planId, NotifyType.DELIVERED,
                "饭送到啦～", location == null ? "快来取饭吧" : "放在" + location + "，快来取吧");
    }

    // ===================== 完成（发爱心值） =====================

    @Override
    @Transactional
    public Long complete(Long planId) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        return doComplete(plan, false);
    }

    /**
     * 完成的唯一实现：手动领取与 24h 自动完成共用，保证加分逻辑一致。
     *
     * <p>顺序固定为「先改状态，再发爱心值」——状态更新失败时不会留下孤立加分记录；
     * 加分重复（唯一索引冲突）时状态已改，符合"只加一次"语义（D7）。
     *
     * @param auto true 表示定时任务触发，走 SYSTEM 角色
     */
    private Long doComplete(MealPlan plan, boolean auto) {
        Long me = auto ? null : UserContext.getUserId();
        Long planId = plan.getId();
        LocalDateTime now = LocalDateTime.now();
        stateMachine.transit(auto ? PlanAction.AUTO_COMPLETE : PlanAction.COMPLETE, plan, me,
                wrapper -> wrapper.set(MealPlan::getCompletedTime, now));
        try {
            loveValueService.award(plan.getCoupleId(), plan.getCarrierId(), planId);
        } catch (DuplicateKeyException e) {
            // uk_love_value_plan_user：同一约定对同一人只加一次，重复视为成功
            log.info("爱心值重复发放已忽略 planId={}", planId);
        }
        String desc = auto ? "超过 24 小时未确认，已自动完成" : "TA 已领取这顿饭";
        notifyService.push(plan.getCoupleId(), plan.getCarrierId(), planId, NotifyType.COMPLETED,
                "这顿饭完成啦", desc);
        notifyService.push(plan.getCoupleId(), plan.getOrdererId(), planId, NotifyType.COMPLETED,
                "这顿饭完成啦", desc);
        return loveValueService.summary(plan.getCoupleId(), plan.getCarrierId()).getTotal();
    }

    // ===================== 取消 =====================

    @Override
    @Transactional
    public void cancel(Long planId, String reason) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        String finalReason = trimToNull(reason);
        if (finalReason == null) {
            throw new BizException(ErrorCode.CANCEL_REASON_REQUIRED);
        }
        if (finalReason.length() > CANCEL_REASON_MAX) {
            throw new BizException(ErrorCode.CANCEL_REASON_REQUIRED, "原因不能超过 " + CANCEL_REASON_MAX + " 个字");
        }
        doCancel(plan, me, finalReason, PlanAction.CANCEL);
    }

    /**
     * 取消的统一落库：状态机把 0/1/2 -> 5（已交付起已在白名单拦住），随后写 cancel_log。
     */
    private void doCancel(MealPlan plan, Long me, String reason, PlanAction action) {
        Long planId = plan.getId();
        stateMachine.transit(action, plan, me,
                wrapper -> wrapper.set(MealPlan::getCancelledTime, LocalDateTime.now()));
        CancelLog cancelLog = new CancelLog();
        cancelLog.setId(IdUtil.nextId());
        cancelLog.setPlanId(planId);
        cancelLog.setCoupleId(plan.getCoupleId());
        cancelLog.setOperatorId(me);
        cancelLog.setReason(reason);
        try {
            cancelLogMapper.insert(cancelLog);
        } catch (DuplicateKeyException e) {
            // uk_cancel_log_plan：已经取消过 -> 与状态冲突同义
            throw new BizException(ErrorCode.STATUS_CONFLICT);
        }
        // 取消不发爱心值
    }

    // ===================== 改派 =====================

    @Override
    @Transactional
    public void reassign(Long planId, Long carrierId) {
        Long me = UserContext.getUserId();
        MealPlan plan = loadOwn(planId);
        // 双人关系里改派对象只能是对方：给出 carrierId 时先做归属校验，防止指向关系外的人
        if (carrierId != null && !isMember(plan.getCoupleId(), carrierId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "改派对象不是你们俩之一");
        }
        stateMachine.transit(PlanAction.REASSIGN, plan, me, wrapper -> {
        });
        notifyService.push(plan.getCoupleId(), plan.getOrdererId(), planId, NotifyType.CLAIMED,
                "这顿饭谁来带？", "对方把饭退回待认领了");
    }

    // ===================== 定时任务入口（无用户会话） =====================

    @Override
    public List<Long> findAutoCompleteIds(LocalDateTime deadline, int limit) {
        // idx_meal_plan_status_delivered：status=3 且 delivered_time 早于阈值
        return selectIdsByWrapper(new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getStatus, PlanStatus.DELIVERED.getCode())
                .lt(MealPlan::getDeliveredTime, deadline), limit);
    }

    @Override
    public List<Long> findExpireIds(LocalDate planDate, int limit) {
        // idx_meal_plan_status_date：未交付（0/1/2）且约定日期已过
        return selectIdsByWrapper(new LambdaQueryWrapper<MealPlan>()
                .in(MealPlan::getStatus, PlanStatus.WAIT_CLAIM.getCode(),
                        PlanStatus.WAIT_ACCEPT.getCode(), PlanStatus.COOKING.getCode())
                .le(MealPlan::getPlanDate, planDate), limit);
    }

    @Override
    @Transactional
    public void autoComplete(Long planId) {
        MealPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            return;
        }
        doComplete(plan, true);
    }

    @Override
    @Transactional
    public void expire(Long planId) {
        MealPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            return;
        }
        // 系统身份（me=null）才会通过转移表的 SYSTEM 角色
        stateMachine.transit(PlanAction.EXPIRE, plan, null, wrapper -> {
        });
    }

    // ===================== 私有工具 =====================

    /** 只扫主键 id，避免把整行拉进内存；limit 为内部常量，不拼用户输入。 */
    private List<Long> selectIdsByWrapper(LambdaQueryWrapper<MealPlan> wrapper, int limit) {
        List<MealPlan> plans = planMapper.selectList(wrapper.select(MealPlan::getId).last("LIMIT " + limit));
        return plans.stream().map(MealPlan::getId).toList();
    }

    /** 按主键取约定并做归属兜底：不属于当前关系一律 1002（不泄露"数据存在"）。 */
    private MealPlan loadOwn(Long planId) {
        Long coupleId = UserContext.requireCoupleId();
        MealPlan plan = planMapper.selectById(planId);
        if (plan == null || !coupleId.equals(plan.getCoupleId())) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        return plan;
    }

    /** 关系的另一方：新约定通知的收件人 / 主动带时的默认点餐方。 */
    private Long partnerOf(Long coupleId, Long me) {
        Couple couple = coupleMapper.selectById(coupleId);
        if (couple == null || couple.getUserA() == null || couple.getUserB() == null) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        return me.equals(couple.getUserA()) ? couple.getUserB() : couple.getUserA();
    }

    /** carrierId 是否属于这段关系（双人产品，改派对象只能是其中一人）。 */
    private boolean isMember(Long coupleId, Long userId) {
        Couple couple = coupleMapper.selectById(coupleId);
        return couple != null && (userId.equals(couple.getUserA()) || userId.equals(couple.getUserB()));
    }

    /** 忌口提醒：命中 avoid_level >= 2 只提醒不拦截（PRD Q2）。 */
    private List<Warning> buildWarnings(Long coupleId, List<String> names) {
        Map<String, Integer> levels = menuService.findAvoidLevels(coupleId, new LinkedHashSet<>(names));
        if (levels == null || levels.isEmpty()) {
            return List.of();
        }
        List<Warning> warnings = new ArrayList<>();
        for (String name : names) {
            Integer level = levels.get(name);
            if (level != null && level >= AvoidLevel.NO.getCode()) {
                Warning warning = new Warning();
                warning.name = name;
                warning.avoidLevel = level;
                warnings.add(warning);
            }
        }
        return warnings;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
