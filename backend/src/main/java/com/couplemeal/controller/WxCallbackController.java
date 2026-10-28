package com.couplemeal.controller;

import com.couplemeal.service.MediaAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信回调控制器（免 token，见 WebConfig 排除项 / 00-全局约定.md §3）。
 *
 * <p>目前只接 mediaCheckAsync 的异步审核结果：
 * 微信 POST 形如 {@code {trace_id, result:{suggest,label}, errcode}}（事件推送时还带 ToUserName 等字段）。
 * 回调无签名校验（微信机制如此），防护靠「只接受库里存在的 trace_id」，
 * 因此未知 trace_id 一律忽略；无论处理结果如何都返回 {@code {"errcode":0}}，避免微信反复重推。
 */
@Slf4j
@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
public class WxCallbackController {

    private final MediaAuditService mediaAuditService;

    /**
     * 图片审核异步回调。
     *
     * @return 固定 {"errcode":0}，告知微信接收成功
     */
    @PostMapping("/media-check-callback")
    public Map<String, Object> mediaCheckCallback(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String traceId = body == null ? null : str(body.get("trace_id"));
            String suggest = extractSuggest(body);
            if (traceId == null || traceId.isBlank()) {
                log.warn("审核回调缺少 trace_id，忽略 body={}", body);
            } else if (suggest == null) {
                log.warn("审核回调缺少 result.suggest，忽略 traceId={}", traceId);
            } else {
                mediaAuditService.handleCallback(traceId, suggest);
            }
        } catch (Exception e) {
            // 回调必须应答 errcode=0，异常只记日志，交给补偿任务兜底
            log.error("审核回调处理异常", e);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("errcode", 0);
        return resp;
    }

    /** 取 result.suggest；兼容 suggest 平铺在根节点的写法 */
    private String extractSuggest(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object result = body.get("result");
        if (result instanceof Map<?, ?> rm) {
            String s = str(rm.get("suggest"));
            if (s != null) {
                return s;
            }
        }
        return str(body.get("suggest"));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
