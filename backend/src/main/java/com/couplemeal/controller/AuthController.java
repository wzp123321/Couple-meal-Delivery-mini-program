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
 * 鉴权控制器：微信登录（免 token）。
 *
 * <p>路径：POST /api/auth/login（context-path=/api）。
 * 返回体 data 为令牌字符串；首次登录与重复登录都只返回令牌，M0 不含关系/资料逻辑（见 M1）。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody LoginReq req) {
        String token = authService.login(req.getCode());
        return R.ok(token);
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
    }

    @Data
    public static class MeResp {
        private Long userId;
        private Boolean bound;
    }
}
