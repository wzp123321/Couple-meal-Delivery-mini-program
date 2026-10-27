package com.couplemeal.service.impl;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.common.util.JwtUtil;
import com.couplemeal.config.WxConfig;
import com.couplemeal.entity.User;
import com.couplemeal.mapper.UserMapper;
import com.couplemeal.service.AuthService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 鉴权服务实现。
 *
 * <p>流程：code -> code2session 换 openid -> 按 openid 查/建用户 -> 签发 JWT。
 * 同一微信用户重复登录复用已有账号（见 couple-binding spec「微信登录建立用户身份」）。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 微信 code2session 接口 */
    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final WxConfig wxConfig;
    private final RestTemplate wxRestTemplate;

    public AuthServiceImpl(UserMapper userMapper, JwtUtil jwtUtil,
                           WxConfig wxConfig, RestTemplate wxRestTemplate) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.wxConfig = wxConfig;
        this.wxRestTemplate = wxRestTemplate;
    }

    @Override
    public String login(String code) {
        String openid = code2session(code);
        User user = userMapper.selectByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setId(IdUtil.nextId());
            user.setOpenid(openid);
            userMapper.insert(user);
            log.info("新建微信用户 openid={}", openid);
        }
        return jwtUtil.generate(user.getId());
    }

    /** 调用微信 code2session，成功返回 openid，失败抛 WX_API_FAILED */
    private String code2session(String code) {
        String url = String.format(CODE2SESSION_URL,
                wxConfig.getAppid(), wxConfig.getSecret(), code);
        try {
            Code2SessionResp resp = wxRestTemplate.getForObject(url, Code2SessionResp.class);
            if (resp == null) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            if (resp.getErrcode() != null && resp.getErrcode() != 0) {
                log.warn("code2session 失败 errcode={} errmsg={}", resp.getErrcode(), resp.getErrmsg());
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            if (resp.getOpenid() == null) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            return resp.getOpenid();
        } catch (RestClientException e) {
            log.error("code2session 调用异常", e);
            throw new BizException(ErrorCode.WX_API_FAILED);
        }
    }

    /** code2session 响应（忽略未映射字段） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Code2SessionResp {
        private String openid;
        private String sessionKey;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }
}
