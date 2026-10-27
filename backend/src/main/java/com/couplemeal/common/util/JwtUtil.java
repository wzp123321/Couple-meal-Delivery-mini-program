package com.couplemeal.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析访问令牌。
 *
 * <p>令牌内容只放 userId（subject）；coupleId 不放进 token，由拦截器实时查关系（见 AuthInterceptor）。
 * 令牌有效期 30 天（见 00-全局约定.md §3）。secret 必须来自环境变量，禁止写死。
 */
@Component
public class JwtUtil {

    @Value("${couple-meal.jwt.secret}")
    private String secret;

    @Value("${couple-meal.jwt.expire-days:30}")
    private int expireDays;

    private SecretKey key;

    private static final String CLAIM_USER_ID = "uid";

    @PostConstruct
    public void init() {
        // 要求密钥 ≥ 256bit（HS256），不足则补齐，避免运行时异常
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 为指定用户签发令牌 */
    public String generate(Long userId) {
        long now = System.currentTimeMillis();
        Date exp = new Date(now + (long) expireDays * 24 * 60 * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(new Date(now))
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** 解析令牌，返回 userId；令牌非法/过期抛 BizException(NOT_LOGIN) */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get(CLAIM_USER_ID, Long.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
    }
}
