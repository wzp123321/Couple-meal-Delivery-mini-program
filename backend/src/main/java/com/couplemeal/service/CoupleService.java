package com.couplemeal.service;

/**
 * 关系服务（M0 仅暴露 coupleId 解析，绑定逻辑见 M1）。
 */
public interface CoupleService {

    /**
     * 解析用户当前所属的有效关系 id。无有效关系返回 null。
     * 带 60 秒本地缓存（见 00-全局约定.md §3，避免每次请求都查库）。
     */
    Long resolveCoupleId(Long userId);
}
