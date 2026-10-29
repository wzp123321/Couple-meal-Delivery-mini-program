package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.SubscribeMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订阅消息接口（phase-2）。仅暴露前端申请授权所需的模板 ID 列表；
 * 真正的下发由 NotifyService / PlanService 在关键事件触发，后端不下发授权请求。
 */
@RestController
@RequestMapping("/subscribe")
@RequiredArgsConstructor
public class SubscribeController {

    private final SubscribeMessageService subscribeMessageService;

    /** 返回各关键事件对应的订阅模板 ID（未配置为空串），供前端对齐 wx.requestSubscribeMessage。 */
    @GetMapping("/templates")
    public R<Map<String, String>> templates() {
        return R.ok(subscribeMessageService.listTemplates());
    }
}
