package com.couplemeal.domain;

import lombok.Getter;

/**
 * 约定动作枚举 —— 状态机的唯一输入。
 *
 * <p>见 {@code 01-通用机制.md §1}：Controller 只收「动作」，绝不收 status（D8），
 * 状态一律由 {@code 动作 + 当前状态 + 我的角色} 推导出来。
 *
 * <p>每个动作的合法性（来源状态 / 可用角色 / 目标状态）不写在本枚举里，
 * 而由 {@code PlanStateMachine} 的一张静态转移表集中承载，
 * 本枚举只定义「有哪些动作」和「谁在操作」这层语义。
 */
@Getter
public enum PlanAction {

    /** 认领：待认领 -> 备餐中，点餐场景专用（D15 一步到位） */
    CLAIM("认领"),
    /** 撤回：带饭方退回待认领，不是取消，不写 cancel_log */
    WITHDRAW("撤回"),
    /** 确认接收：主动带场景下点餐方点头 -> 备餐中 */
    CONFIRM("确认接收"),
    /** 婉拒：主动带场景下点餐方不接受 -> 已取消 */
    REJECT("婉拒"),
    /** 标记送达：备餐中 -> 已交付（开始 24h 自动完成倒计时） */
    DELIVER("标记送达"),
    /** 已领取：已交付 -> 已完成，触发爱心值 */
    COMPLETE("已领取"),
    /** 系统自动完成：已交付超 24h -> 已完成，照发爱心值 */
    AUTO_COMPLETE("自动完成"),
    /** 取消：0/1/2 -> 已取消，必填原因，写 cancel_log，不发爱心值 */
    CANCEL("取消"),
    /** 改派：带饭方把饭退回待认领 */
    REASSIGN("改派"),
    /** 系统过期：次日 02:30 仍未交付的 0/1/2 -> 已过期 */
    EXPIRE("过期");

    private final String desc;

    PlanAction(String desc) {
        this.desc = desc;
    }

    /**
     * 操作人相对本约定的角色 —— actions[] 与转移表的判定维度之一。
     *
     * <p>只按 status 推断会把「点餐方的按钮」给带饭方看（{@code 20-约定/后端.md §6}），
     * 所以判定必须带 (status, 角色) 两个维度。
     */
    public enum Role {

        /** 点餐方（想吃的人 / 接收方） */
        ORDERER,
        /** 带饭方（已认领或主动带的人） */
        CARRIER,
        /** 关系内的另一方：带饭方尚未确定时的「潜在带饭方」（如待认领时看到「我来带」的人） */
        MEMBER,
        /** 系统（定时任务）：调用方没有用户会话，传 null 即此角色 */
        SYSTEM
    }
}
