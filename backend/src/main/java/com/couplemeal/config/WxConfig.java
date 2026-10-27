package com.couplemeal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 微信配置：appid / secret（仅后端持有，来自环境变量）与 RestTemplate。
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

    @Bean
    public RestTemplate wxRestTemplate() {
        return new RestTemplate();
    }
}
