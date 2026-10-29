package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.domain.enums.NotifyType;
import com.couplemeal.entity.Notification;
import com.couplemeal.mapper.NotificationMapper;
import com.couplemeal.service.NotifyService;
import com.couplemeal.service.SubscribeMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 站内通知服务实现（红点来源，见 50-通知/后端.md）。
 *
 * <p>MVP 只写库、不做推送通道（微信订阅消息二期）。红点由前端进 page 时拉 {@code /api/msg/unread}。
 *
 * <p>去重要点：
 *  1. {@code push} 是唯一写入入口，**不先查再插** —— 并发下必漏判，交给
 *     {@code uk_notification_plan_type_receiver} 拒绝（见 01-通用机制.md §2 原则 1）。
 *  2. 只 catch {@link DuplicateKeyException} → 重复推送静默忽略。
 *  3. {@code planId} 为 null 的每日提醒不参与该唯一约束（MySQL 唯一索引对 NULL 不去重），
 *     它的"当天只发一次"由 {@link com.couplemeal.job.DailyRemindJob} 自己判重。
 *
 * <p>文案基调见 PRD §12.11：情感化，不用「订单 / 配送 / 下单」等交易词。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl implements NotifyService {

    /** 未读 / 已读 */
    private static final int UNREAD = 0;
    private static final int READ = 1;

    /** 每日提醒文案（type=5，见 50-通知/后端.md §3.2） */
    public static final String DAILY_REMIND_TITLE = "今天的饭还没人认领";
    public static final String DAILY_REMIND_CONTENT = "TA 还等着呢，有空的话就带一份吧";

    private final NotificationMapper notificationMapper;
    private final SubscribeMessageService subscribeMessageService;

    // ===================== 写入（唯一入口） =====================

    @Override
    @Transactional
    public void push(Long coupleId, Long receiverId, Long planId, NotifyType type, String title, String content) {
        Notification n = new Notification();
        n.setId(IdUtil.nextId());
        n.setCoupleId(coupleId);
        n.setReceiverId(receiverId);
        n.setPlanId(planId);
        n.setType(type.getCode());
        n.setTitle(title);
        n.setContent(content);
        n.setReadFlag(UNREAD);
        n.setCreateTime(LocalDateTime.now());
        try {
            notificationMapper.insert(n);
        } catch (DuplicateKeyException e) {
            // 同约定 + 同类型 + 同收件人已发过，静默忽略
            log.debug("通知重复推送已忽略 receiverId={} planId={} type={}", receiverId, planId, type);
        }
        // 订阅消息是站内通知的增量通道：失败仅记录，不阻断主流程（也不新增站内行）
        subscribeMessageService.send(receiverId, type, title, content);
    }

    @Override
    public long countUnread(Long receiverId) {
        // 走 idx_notification_receiver_read(receiver_id, read_flag, create_time)，量小够用，不做缓存
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getReadFlag, UNREAD));
    }

    @Override
    public void markAllRead(Long receiverId) {
        Notification patch = new Notification();
        patch.setReadFlag(READ);
        patch.setReadTime(LocalDateTime.now());
        notificationMapper.update(patch, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getReadFlag, UNREAD));
    }

    // ===================== 列表（消息中心） =====================

    /**
     * 消息列表（{@code GET /api/msg/list}），按时间倒序分页。
     *
     * <p>未被 {@link NotifyService} 接口固化（接口由 20/60 模块共用的三个方法组成），
     * 故只在实现类上新增；但**必须带 coupleId**，否则解绑换人后能看到上一段关系的通知（§6 易错点）。
     *
     * @param coupleId 关系隔离键
     * @param current  页码（1 起）
     * @param size     每页条数
     */
    public IPage<Notification> list(Long coupleId, Long receiverId, long current, long size) {
        return notificationMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<Notification>()
                .eq(Notification::getCoupleId, coupleId)
                .eq(Notification::getReceiverId, receiverId)
                .orderByDesc(Notification::getCreateTime));
    }
}
