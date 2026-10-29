package com.couplemeal.config;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信配置：appid / secret（仅后端持有，来自环境变量）、RestTemplate，
 * 以及 content 安全审核所需的能力（access_token 缓存 + imgSecCheck / msgSecCheck）。
 *
 * <p>审核接口说明（见 后端.md §3.6）：
 *   - imgSecCheck：小图同步审核（头像），限额 1MB / 750×1334，我们远小于。
 *   - msgSecCheck v2：文本审核（昵称），需传用户 openid + scene。
 * 两个接口返回 errcode=0 通过；errcode=87014 内容违规（由调用方决定拒绝）；
 * 其余 errcode 视为微信接口异常，抛 WX_API_FAILED 让用户重试。
 */
@Configuration
public class WxConfig {

    @Value("${couple-meal.wx.appid}")
    private String appid;

    @Value("${couple-meal.wx.secret}")
    private String secret;

    public String getAppid() {
        return appid;
    }

    public String getSecret() {
        return secret;
    }

    /** 出网调用（微信）超时上限：连接 2s、读取 3s，避免微信不可达时无限阻塞主流程（如打卡提交同步审核） */
    private static final int WX_CONNECT_TIMEOUT_MS = 2000;
    private static final int WX_READ_TIMEOUT_MS = 3000;

    @Bean
    public RestTemplate wxRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WX_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(WX_READ_TIMEOUT_MS);
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }

    /** access_token 缓存（微信限制每日获取次数，需缓存并提前刷新） */
    private volatile String accessToken;
    private volatile long tokenExpireAt = 0L;

    /**
     * 获取并缓存 access_token（提前 60s 刷新，避免临界点失效）。
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < tokenExpireAt - 60_000L) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000L) {
                return accessToken;
            }
            String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                    + "&appid=" + appid + "&secret=" + secret;
            try {
                TokenResp resp = wxRestTemplate().getForObject(url, TokenResp.class);
                if (resp == null || resp.getErrcode() != null) {
                    logWarn(resp);
                    throw new BizException(ErrorCode.WX_API_FAILED);
                }
                accessToken = resp.getAccessToken();
                long expiresIn = resp.getExpiresIn() != null ? resp.getExpiresIn() : 7200;
                tokenExpireAt = System.currentTimeMillis() + expiresIn * 1000L;
                return accessToken;
            } catch (RestClientException e) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
        }
    }

    private void logWarn(TokenResp resp) {
        if (resp != null) {
            // 仅记录，不打印 secret
            System.out.println("微信 token 获取失败 errcode=" + resp.getErrcode());
        }
    }

    /**
     * 发送订阅消息（模板消息）。失败抛 WX_API_FAILED；调用方通常就地吞掉，
     * 不阻断主流程（订阅消息只是系统内通知的增量通道，见 subscribe-message spec）。
     *
     * @param openid     接收者 openid（来自 user.openid）
     * @param templateId 模板 ID（来自配置；空串由调用方前置判断，这里仍会打到微信）
     * @param data       模板字段，结构为 {fieldName: {value: "..."}}，字段名需与 MP 后台注册模板一致
     */
    public void sendSubscribeMessage(String openid, String templateId, Map<String, Object> data) {
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("data", data);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<WxBaseResp> resp = wxRestTemplate().postForEntity(url, request, WxBaseResp.class);
            WxBaseResp r = resp.getBody();
            if (r == null || (r.getErrcode() != null && r.getErrcode() != 0)) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.WX_API_FAILED);
        }
    }

    /**
     * 图片同步审核（小图接口）。返回 true=通过，false=内容违规（87014）。
     * 其它异常抛 WX_API_FAILED。
     */
    public boolean imgSecCheck(byte[] image) {
        String url = "https://api.weixin.qq.com/wxa/img_sec_check?access_token=" + getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("media", new ByteArrayResource(image));
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<WxBaseResp> resp = wxRestTemplate().postForEntity(url, request, WxBaseResp.class);
            WxBaseResp r = resp.getBody();
            if (r == null) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            if (r.getErrcode() == null || r.getErrcode() == 0) {
                return true;
            }
            if (r.getErrcode() == 87014) {
                return false;
            }
            throw new BizException(ErrorCode.WX_API_FAILED);
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.WX_API_FAILED);
        }
    }

    /**
     * 文本审核（v2，需 openid）。返回 true=通过，false=内容违规（87014）。
     * 其它异常抛 WX_API_FAILED。
     */
    public boolean msgSecCheck(String content, String openid) {
        String url = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=" + getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("openid", openid);
        body.put("scene", 2);
        body.put("version", "2");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<WxBaseResp> resp = wxRestTemplate().postForEntity(url, request, WxBaseResp.class);
            WxBaseResp r = resp.getBody();
            if (r == null) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            if (r.getErrcode() == null || r.getErrcode() == 0) {
                return true;
            }
            if (r.getErrcode() == 87014) {
                return false;
            }
            throw new BizException(ErrorCode.WX_API_FAILED);
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.WX_API_FAILED);
        }
    }

    /** RestTemplate 上传 multipart 需要的 ByteArrayResource（必须提供文件名） */
    private static class ByteArrayResource extends org.springframework.core.io.ByteArrayResource {
        ByteArrayResource(byte[] data) {
            super(data);
        }

        @Override
        public String getFilename() {
            return "file.jpg";
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenResp {
        private String accessToken;
        private Integer expiresIn;
        private Integer errcode;
        private String errmsg;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WxBaseResp {
        private Integer errcode;
        private String errmsg;
    }
}
