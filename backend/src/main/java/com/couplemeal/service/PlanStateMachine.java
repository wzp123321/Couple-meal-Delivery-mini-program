package com.couplemeal.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.domain.PlanAction;
import com.couplemeal.domain.PlanAction.Role;
import com.couplemeal.domain.enums.PlanSource;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.mapper.MealPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 约定状态机 —— 唯一的状态改写入闸口（D8 / D9）。
 *
 * <p>三件事：白名单校验（{@link #assertCan}）、可执行动作清单（{@link #availableActions}）、
 * 条件 UPDATE（{@link #updateStatus}）。<b>不做</b>通知 / 爱心值 / 流水等副作用，
 * 那些由 {@code PlanService} 统一编排（见 {@code 01-通用机制.md §1.3}）。
 *
 * <p>实现方式是一张 {@code Map<PlanAction, Transition>} 静态表，新增动作只需加一行表项，
 * 不会散落成 if-else。转移表的语义：在某个状态下，哪些角色可以执行这个动作。
 */
@Service
@RequiredArgsConstructor
public class PlanStateMachine {

    private final MealPlanMapper planMapper;

    /**
     * 一条转移规则。
     *
     * @param rolesByStatus key = 允许的来源状态，value = 该状态下允许的角色；不在 key 里的状态一律拒绝
     * @param source        限定的约定来源，null = 不限制
     * @param to            目标状态
     * @param clearCarrier  是否顺带清空 carrier_id / claimed_time（撤回、改派退回待认领时用）
     */
    private record Transition(Map<PlanStatus, Set<Role>> rolesByStatus, PlanSource source,
                              PlanStatus to, boolean clearCarrier) {
    }

    /** 转移白名单（{@code 01-通用机制.md §1.2} 判定表 + PRD §12.4 权限矩阵的机器可读版） */
    private static final Map<PlanAction, Transition> TRANSITIONS = new EnumMap<>(PlanAction.class);

    /** 前端详情页要展示的动作（不含 AUTO_COMPLETE / EXPIRE 这两个系统动作） */
    private static final List<PlanAction> UI_ACTIONS = List.of(
            PlanAction.CLAIM, PlanAction.WITHDRAW, PlanAction.CONFIRM, PlanAction.REJECT,
            PlanAction.DELIVER, PlanAction.COMPLETE, PlanAction.CANCEL, PlanAction.REASSIGN);

    static {
        // 待认领 --对方认领--> 备餐中（点餐场景一步到位，D15）
        TRANSITIONS.put(PlanAction.CLAIM, new Transition(
                Map.of(PlanStatus.WAIT_CLAIM, Set.of(Role.MEMBER)),
                PlanSource.ORDER, PlanStatus.COOKING, false));
        // 带饭方退回收件箱，不是取消
        TRANSITIONS.put(PlanAction.WITHDRAW, new Transition(
                Map.of(PlanStatus.COOKING, Set.of(Role.CARRIER)),
                PlanSource.ORDER, PlanStatus.WAIT_CLAIM, true));
        // 主动带：接收方点头才算数（防一方单方面决定）
        TRANSITIONS.put(PlanAction.CONFIRM, new Transition(
                Map.of(PlanStatus.WAIT_ACCEPT, Set.of(Role.ORDERER)),
                PlanSource.BRING, PlanStatus.COOKING, false));
        TRANSITIONS.put(PlanAction.REJECT, new Transition(
                Map.of(PlanStatus.WAIT_ACCEPT, Set.of(Role.ORDERER)),
                PlanSource.BRING, PlanStatus.CANCELLED, false));
        // 汇合后的主线
        TRANSITIONS.put(PlanAction.DELIVER, new Transition(
                Map.of(PlanStatus.COOKING, Set.of(Role.CARRIER)),
                null, PlanStatus.DELIVERED, false));
        TRANSITIONS.put(PlanAction.COMPLETE, new Transition(
                Map.of(PlanStatus.DELIVERED, Set.of(Role.ORDERER)),
                null, PlanStatus.COMPLETED, false));
        TRANSITIONS.put(PlanAction.AUTO_COMPLETE, new Transition(
                Map.of(PlanStatus.DELIVERED, Set.of(Role.SYSTEM)),
                null, PlanStatus.COMPLETED, false));
        // 旁路
        TRANSITIONS.put(PlanAction.CANCEL, new Transition(
                Map.of(PlanStatus.WAIT_CLAIM, Set.of(Role.ORDERER),
                        PlanStatus.WAIT_ACCEPT, Set.of(Role.CARRIER),
                        PlanStatus.COOKING, Set.of(Role.ORDERER, Role.CARRIER)),
                null, PlanStatus.CANCELLED, false));
        TRANSITIONS.put(PlanAction.EXPIRE, new Transition(
                Map.of(PlanStatus.WAIT_CLAIM, Set.of(Role.SYSTEM),
                        PlanStatus.WAIT_ACCEPT, Set.of(Role.SYSTEM),
                        PlanStatus.COOKING, Set.of(Role.SYSTEM)),
                null, PlanStatus.EXPIRED, false));
        // 改派：仅带饭方，退回待认领；主动带没有「待认领」可回，故限定 source=ORDER
        TRANSITIONS.put(PlanAction.REASSIGN, new Transition(
                Map.of(PlanStatus.WAIT_ACCEPT, Set.of(Role.CARRIER),
                        PlanStatus.COOKING, Set.of(Role.CARRIER)),
                PlanSource.ORDER, PlanStatus.WAIT_CLAIM, true));
    }

    // ===================== 校验 =====================

    /**
     * 判断「我」此刻能否对该约定执行该动作，通过则返回目标状态（供调用方做后续字段更新）。
     *
     * <p>终态（已完成 / 已取消 / 已过期）不在任何一条规则的来源状态里，天然只读，返回 1004。
     *
     * @param action 动作
     * @param plan   目标约定（已校验过 coupleId 归属）
     * @param me     操作人 userId；定时任务传 null
     * @throws BizException 1004 状态不允许；认领场景点餐方本人点「我来带」时为 1005
     */
    public PlanStatus assertCan(PlanAction action, MealPlan plan, Long me) {
        Transition t = TRANSITIONS.get(action);
        if (t == null) {
            throw new BizException(ErrorCode.STATUS_CONFLICT);
        }
        PlanStatus from = PlanStatus.of(plan.getStatus());
        Role role = roleOf(plan, me);
        // 状态 / 角色 / 来源三项判定统一走 can()，避免校验与 actions[] 计算口径漂移
        if (!can(t, role, from, plan)) {
            throw reject(action, role);
        }
        return t.to();
    }

    /**
     * 当前用户对该约定可执行的动作列表（小写动作名，前端据此决定按钮显隐）。
     *
     * <p>这是前后端关于「按钮能不能点」的唯一判定来源，前端不自己推状态
     * （详见 {@code 20-约定/后端.md §3.8}）。
     */
    public List<String> availableActions(MealPlan plan, Long me) {
        PlanStatus from = PlanStatus.of(plan.getStatus());
        Role role = roleOf(plan, me);
        List<String> actions = new ArrayList<>();
        for (PlanAction action : UI_ACTIONS) {
            Transition t = TRANSITIONS.get(action);
            if (t != null && can(t, role, from, plan)) {
                actions.add(action.name().toLowerCase(Locale.ROOT));
            }
        }
        return actions;
    }

    /**
     * 我在本约定中的角色。
     *
     * @param me null 表示没有用户会话，一律按系统（定时任务）处理
     */
    public Role roleOf(MealPlan plan, Long me) {
        if (me == null) {
            return Role.SYSTEM;
        }
        if (me.equals(plan.getOrdererId())) {
            return Role.ORDERER;
        }
        if (plan.getCarrierId() != null && me.equals(plan.getCarrierId())) {
            return Role.CARRIER;
        }
        return Role.MEMBER;
    }

    // ===================== 写入 =====================

    /**
     * 校验 + 条件更新一步到位：白名单通过后执行统一的条件 UPDATE，行数为 0 即并发冲突。
     *
     * @param extraSetter 追加本次转移要写的业务字段（如 claimed_time / delivered_time）
     * @return 目标状态
     */
    public PlanStatus transit(PlanAction action, MealPlan plan, Long me,
                              Consumer<LambdaUpdateWrapper<MealPlan>> extraSetter) {
        PlanStatus to = assertCan(action, plan, me);
        Transition t = TRANSITIONS.get(action);
        updateStatus(plan.getId(), plan.getStatus(), to.getCode(), plan.getVersion(), wrapper -> {
            if (t.clearCarrier()) {
                // 退回待认领：带饭方位置重新空出来
                wrapper.setSql("carrier_id = NULL, claimed_time = NULL");
            }
            extraSetter.accept(wrapper);
        });
        return to;
    }

    /**
     * 唯一的状态写入方式：条件 UPDATE + affected rows（D9）。
     *
     * <p>实体上没有 {@code @Version}，乐观锁靠这里的 {@code version = ?} 条件手写实现：
     * 行数为 0 说明状态或版本已被别人改过（被并发抢走 / 已被取消），统一返回 1004。
     */
    public void updateStatus(Long planId, Integer from, Integer to, Integer version,
                             Consumer<LambdaUpdateWrapper<MealPlan>> extraSetter) {
        LambdaUpdateWrapper<MealPlan> wrapper = new LambdaUpdateWrapper<MealPlan>()
                .eq(MealPlan::getId, planId)
                .eq(MealPlan::getStatus, from)
                .eq(MealPlan::getVersion, version)
                .set(MealPlan::getStatus, to)
                .setSql("version = version + 1");
        if (extraSetter != null) {
            extraSetter.accept(wrapper);
        }
        int rows = planMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATUS_CONFLICT);
        }
    }

    /** 只改状态的简版。 */
    public void updateStatus(Long planId, Integer from, Integer to, Integer version) {
        updateStatus(planId, from, to, version, null);
    }

    // ===================== 内部 =====================

    /** 来源状态 + 角色 + 来源渠道三项全部命中才算通过。 */
    private boolean can(Transition t, Role role, PlanStatus from, MealPlan plan) {
        if (t.source() != null && !Objects.equals(plan.getSource(), t.source().getCode())) {
            return false;
        }
        Set<Role> allowed = t.rolesByStatus().get(from);
        return allowed != null && allowed.contains(role);
    }

    /** 认领场景里「点餐方点我来带」是比「状态不允许」更具体的错误，单列 1005。 */
    private BizException reject(PlanAction action, Role role) {
        if (action == PlanAction.CLAIM && role == Role.ORDERER) {
            return new BizException(ErrorCode.CANNOT_CLAIM_SELF);
        }
        return new BizException(ErrorCode.STATUS_CONFLICT);
    }
}
