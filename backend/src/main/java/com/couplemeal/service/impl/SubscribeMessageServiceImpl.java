package com.couplemeal.service.impl;

import com.couplemeal.common.BizException;
import com.couplemeal.config.WxConfig;
import com.couplemeal.domain.enums.NotifyType;
import com.couplemeal.entity.User;
import com.couplemeal.mapper.UserMapper;
import com.couplemeal.service.SubscribeMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信订阅消息服务实现（phase-2）。
 *
 * <p>三类关键事件映射模板：被认领(CLAIMED) / 已送达(DELIVERED) / 待打卡提醒(CHECKIN_REMIND)；
 * 模板 ID 来自配置 {@code couple-meal.wx.sub-template.*}，未配置则跳过，dev 无需改 application.yml。
 *
 * <p>模板字段名（thing1/thing2）需与 MP 后台注册的模板一致；本实现用通用占位，
 * 真实上线前按已申请模板的字段名调整（不影响 dev 自测，因 dev 本就发不出去）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeMessageServiceImpl implements SubscribeMessageService {

    private final WxConfig wxConfig;
    private final UserMapper userMapper;

    @Value("${couple-meal.wx.sub-template.claimed:}")
    private String tplClaimed;
    @Value("${couple-meal.wx.sub-template.delivered:}")
    private String tplDelivered;
    @Value("${couple-meal.wx.sub-template.checkin-remind:}")
    private String tplCheckinRemind;

    @Override
    public void send(Long receiverUserId, NotifyType type, String title, String content) {
        String templateId = templateFor(type);
        if (templateId == null || templateId.isBlank()) {
            return; // 该事件未配置模板，跳过
        }
        User user = userMapper.selectById(receiverUserId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            return; // 无 openid，跳過
        }
        Map<String, Object> data = buildData(title, content);
        try {
            wxConfig.sendSubscribeMessage(user.getOpenid(), templateId, data);
        } catch (BizException e) {
            // 微信不可达 / 未授权 / 限频：仅记录，不阻断主流程
            log.warn("订阅消息下发跳过 receiverUserId={} type={} 原因={}", receiverUserId, type, e.getMessage());
        }
    }

    @Override
    public Map<String, String> listTemplates() {
        Map<String, String> map = new HashMap<>(3);
        map.put("claimed", blankToEmpty(tplClaimed));
        map.put("delivered", blankToEmpty(tplDelivered));
        map.put("checkinRemind", blankToEmpty(tplCheckinRemind));
        return map;
    }

    private String templateFor(NotifyType type) {
        return switch (type) {
            case CLAIMED -> tplClaimed;
            case DELIVERED -> tplDelivered;
            case CHECKIN_REMIND -> tplCheckinRemind;
            default -> null; // 非订阅事件，不发
        };
    }

    private Map<String, Object> buildData(String title, String content) {
        Map<String, Object> data = new HashMap<>(2);
        Map<String, String> v1 = new HashMap<>(1);
        v1.put("value", title == null ? "" : title);
        data.put("thing1", v1);
        Map<String, String> v2 = new HashMap<>(1);
        v2.put("value", content == null ? "" : content);
        data.put("thing2", v2);
        return data;
    }

    private String blankToEmpty(String s) {
        return s == null ? "" : s;
    }
}
