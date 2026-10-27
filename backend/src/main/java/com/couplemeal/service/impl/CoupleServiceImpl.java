package com.couplemeal.service.impl;

import com.couplemeal.entity.Couple;
import com.couplemeal.mapper.CoupleMapper;
import com.couplemeal.service.CoupleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关系服务实现：coupleId 解析 + 60 秒本地缓存。
 *
 * <p>设计：coupleId 不放进 token，由拦截器每次请求实时解析（带缓存）。
 * 缓存仅按 userId 记 coupleId 与写入时间戳，过期（60s）后重查，确保解绑/绑定能及时生效。
 */
@Slf4j
@Service
public class CoupleServiceImpl implements CoupleService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final CoupleMapper coupleMapper;
    /** userId -> (coupleId, 写入时间戳)；coupleId 可能为 null */
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public CoupleServiceImpl(CoupleMapper coupleMapper) {
        this.coupleMapper = coupleMapper;
    }

    @Override
    public Long resolveCoupleId(Long userId) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(userId);
        if (entry != null && (now - entry.ts) < CACHE_TTL_MS) {
            return entry.coupleId;
        }
        Couple couple = coupleMapper.selectValidByUserId(userId);
        Long coupleId = couple != null ? couple.getId() : null;
        cache.put(userId, new CacheEntry(coupleId, now));
        return coupleId;
    }

    private record CacheEntry(Long coupleId, long ts) {
    }
}
