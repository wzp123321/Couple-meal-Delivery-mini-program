package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.common.UserContext;
import com.couplemeal.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权控制器：微信登录（免 token）+ 当前登录态探活。
 *
 * <p>路径：POST /api/auth/login（context-path=/api）。
 * 登录返回契约字段 {token, userId, nickname, avatar, hasCouple}（见 后端.md §3.1 / §2 契约）。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<AuthService.LoginResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req.getCode(), req.getAgreedPrivacy()));
    }

    /**
     * 调试/探活：返回当前登录态（userId / 是否已绑定）。
     * 仅用于联调，真实前端不需要。
     */
    @PostMapping("/me")
    public R<MeResp> me() {
        MeResp resp = new MeResp();
        resp.setUserId(UserContext.getUserId());
        resp.setBound(UserContext.getCoupleId() != null);
        return R.ok(resp);
    }

    @Data
    public static class LoginReq {
        @NotBlank(message = "登录凭证不能为空")
        private String code;

        /** 隐私协议同意标记；未同意不得登录（后端.md §3.1） */
        private Boolean agreedPrivacy;
    }

    @Data
    public static class MeResp {
        private Long userId;
        private Boolean bound;
    }
}
