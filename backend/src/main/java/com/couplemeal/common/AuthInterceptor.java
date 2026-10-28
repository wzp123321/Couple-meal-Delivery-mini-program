package com.couplemeal.common;

import com.couplemeal.common.util.JwtUtil;
import com.couplemeal.service.AuthService;
import com.couplemeal.service.CoupleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器（见 00-全局约定.md §3）。
 *
 * <p>职责：解析 Authorization: Bearer <token> -> 得到 userId -> 按主键查 user（注销后 user 已删，
 * 查不到即返回 1001，使旧 token 立即失效）-> 实时解析 coupleId，注入 UserContext（ThreadLocal）。
 *
 * <p>coupleId 不放 token，每次请求重新查（服务内有缓存）。免鉴权路径由 WebConfig 排除。
 * 缺 token / 非法 token / 用户已删除 均抛 BizException(NOT_LOGIN)，由 GlobalExceptionHandler 转 R。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final CoupleService coupleService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(AUTH_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        Long userId = jwtUtil.parseUserId(token);            // 非法/过期 -> NOT_LOGIN
        // 注销后 user 物理删除，按主键查不到即视为未登录（堵住「已删仍能访问」漏洞，见 后端.md §3.5）
        if (authService.getUserById(userId) == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        Long coupleId = coupleService.resolveCoupleId(userId);
        UserContext.set(userId, coupleId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
