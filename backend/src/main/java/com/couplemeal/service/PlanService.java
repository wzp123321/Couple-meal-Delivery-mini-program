package com.couplemeal.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 约定服务（核心模块，见 {@code 20-约定/后端.md}）。
 *
 * <p>职责边界：本类负责「校验 + 编排」——状态一律交给 {@link PlanStateMachine} 改写，
 * 通知 / 爱心值在状态写成功后才调用（副作用放后面，避免"通知发了状态没改"）。
 *
 * <p>请求体由 Controller 的静态内部类承载；这里是出参（与 {@link CoupleService} 保持一致的放置惯例，
 * 业务组装在 Service、Controller 只做参数搬运）。
 */
public interface PlanService {

    /**
     * 发起约定。忌口只回 warnings 不拦截，双来源写出不同的初始状态。
     *
     * @param items 菜品列表，1–10 道，{@link ItemInput#name} 为必填快照名
     */
    CreateResult create(int mealType, int source, LocalDate planDate, List<ItemInput> items, String remark);

    /** 约定详情（含菜品快照与当前用户可执行动作 actions[]） */
    PlanDetail detail(Long planId);

    /** 今日约定列表 */
    List<PlanBrief> today();

    /** 认领：对方接下这顿饭 -> 备餐中 */
    void claim(Long planId);

    /** 撤回：带饭方退回待认领，不写 cancel_log、不发爱心值 */
    void withdraw(Long planId);

    /** 确认接收：主动带场景下点餐方点头 -> 备餐中 */
    void confirm(Long planId);

    /** 婉拒：走取消流程，reason 缺省为「婉拒」 */
    void reject(Long planId, String reason);

    /** 标记送达：写交付位置与便签，-> 已交付 */
    void deliver(Long planId, String deliverLocation, String deliverNote);

    /**
     * 已领取：-> 已完成，事务内先改状态再发爱心值。
     *
     * @return 发放后关系合计爱心值
     */
    Long complete(Long planId);

    /** 取消：0/1/2 可取消，已交付起不可；必填原因，写 cancel_log */
    void cancel(Long planId, String reason);

    /** 改派：仅带饭方，退回待认领 */
    void reassign(Long planId, Long carrierId);

    /** {@code AutoCompleteJob} 扫描：已交付且 delivered_time < deadline 的约定 */
    List<Long> findAutoCompleteIds(LocalDateTime deadline, int limit);

    /** {@code PlanExpireJob} 扫描：未交付（0/1/2）且 plan_date <= date 的约定 */
    List<Long> findExpireIds(LocalDate date, int limit);

    /** 自动完成（系统身份），与手动完成共用同一条加分逻辑 */
    void autoComplete(Long planId);

    /** 转已过期（系统身份），不发爱心值 */
    void expire(Long planId);

    // ===== 出参对象（就近放置，不设 dto 包） =====

    /** 发起结果：约定 id + 忌口强提醒 */
    class CreateResult {
        public Long planId;
        /** 命中 avoid_level >= 2 的菜品，为空表示无提醒 */
        public List<Warning> warnings;
    }

    /** 忌口提醒项 */
    class Warning {
        public String name;
        public Integer avoidLevel;
    }

    /** 发起时的一道题（menuItemId 为空表示手输菜名） */
    class ItemInput {
        public Long menuItemId;
        public String name;
    }

    /** 详情里的菜品快照 */
    class PlanItemVo {
        public Long menuItemId;
        public String name;
        public Integer seq;
    }

    /** 约定详情 */
    class PlanDetail {
        public Long id;
        public Integer mealType;
        public Integer source;
        public Integer status;
        public String statusDesc;
        public LocalDate planDate;
        public String remark;
        public String deliverLocation;
        public String deliverNote;
        public Long ordererId;
        public Long carrierId;
        public Boolean iAmOrderer;
        public Boolean iAmCarrier;
        public LocalDateTime claimedTime;
        public LocalDateTime deliveredTime;
        public LocalDateTime completedTime;
        public LocalDateTime cancelledTime;
        /** 菜名快照（不 join 菜单表，D2） */
        public List<PlanItemVo> items;
        /** 当前用户可执行动作，小写动作名，前端据此显隐按钮 */
        public List<String> actions;
    }

    /** 今日列表项 */
    class PlanBrief {
        public Long id;
        public Integer mealType;
        public Integer source;
        public Integer status;
        public String statusDesc;
        public LocalDate planDate;
        public String remark;
        public Long ordererId;
        public Long carrierId;
        public LocalDateTime deliveredTime;
        public List<String> itemNames;
    }
}
