package com.couplemeal.service;

/**
 * 鉴权服务：微信登录换身份、签发令牌。
 */
public interface AuthService {

    /**
     * 微信登录：用小程序 code 换取 openid，建/查用户，签发访问令牌。
     *
     * @param code 小程序 wx.login 拿到的临时登录凭证
     * @return 访问令牌（JWT），前端缓存后随后续请求带在 Authorization 头
     */
    String login(String code);
}
