package com.couplemeal.service;

import com.couplemeal.domain.enums.NotifyType;

import java.util.Map;

/**
 * 微信订阅消息服务（phase-2，见 openspec/changes/phase-2/specs/subscribe-message）。
 *
 * <p>定位：系统内红点 / 角标的<b>增量</b>通道，二者并存；订阅失败绝不影响系统内通知。
 * 本服务只负责「有模板才发、无则跳过、失败仅记录」，不持有授权逻辑（授权在端上申请）。
 */
public interface SubscribeMessageService {

    /**
     * 向接收者尝试下发订阅消息（仅 CLAIMED / DELIVERED / CHECKIN_REMIND 三类关键事件）。
     * 模板未配置、用户无 openid、微信不可达等任何异常都静默跳过，不抛错。
     */
    void send(Long receiverUserId, NotifyType type, String title, String content);

    /** 返回各事件对应的模板 ID（来自配置；未配置为空串），供前端申请授权时对齐。 */
    Map<String, String> listTemplates();
}
