package com.couplemeal.service;

import com.couplemeal.domain.enums.NotifyType;

/**
 * 站内通知唯一写入入口（红点来源）。
 *
 * <p>去重由数据库唯一约束 {@code uk_notification_plan_type_receiver} 兜底：
 * 同一约定 + 同一类型 + 同一收件人只可能存在一条，重复推送静默忽略，不抛异常。
 * {@code planId} 为 null 的记录（每日提醒）不参与该唯一性约束。
 *
 * <p>约定：服务端只写库，不做推送（见 50-通知/后端.md §3.3）。
 */
public interface NotifyService {

    /**
     * 发送站内通知（去重）。
     *
     * @param coupleId   关系隔离键
     * @param receiverId 收件人
     * @param planId     关联约定，每日提醒传 null
     * @param type       通知类型
     * @param title      标题，可空
     * @param content    正文，可空
     */
    void push(Long coupleId, Long receiverId, Long planId, NotifyType type, String title, String content);

    /**
     * 未读条数（Tab 角标来源）。
     */
    long countUnread(Long receiverId);

    /**
     * 全部已读（进消息列表即清，不做单条已读）。
     */
    void markAllRead(Long receiverId);
}
