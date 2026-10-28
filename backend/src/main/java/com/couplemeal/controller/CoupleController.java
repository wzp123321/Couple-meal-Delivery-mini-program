package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.CoupleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关系控制器：邀请码生成、加入、关系主页、设置、解绑。
 *
 * <p>路径（context-path=/api）：/api/couple/invite、/api/couple/join、/api/couple/info、
 * /api/couple/settings、/api/couple/unbind。全部走 AuthInterceptor 鉴权。
 *
 * <p>所有接口不接收 coupleId 入参（数据隔离铁律）；coupleId 只从 UserContext 取。
 * 二次确认（解绑/注销）由前端完成，服务端不设 confirm 参数（见 后端.md §3.5）。
 */
@RestController
@RequestMapping("/couple")
@RequiredArgsConstructor
public class CoupleController {

    private final CoupleService coupleService;

    @PostMapping("/invite")
    public R<CoupleService.InviteResp> invite() {
        return R.ok(coupleService.generateInvite());
    }

    @PostMapping("/join")
    public R<CoupleService.JoinResp> join(@Valid @RequestBody JoinReq req) {
        return R.ok(coupleService.join(req.getCode()));
    }

    @GetMapping("/info")
    public R<CoupleService.CoupleInfoResp> info() {
        return R.ok(coupleService.info());
    }

    @PostMapping("/settings")
    public R<Void> settings(@Valid @RequestBody CoupleService.SettingsReq req) {
        coupleService.settings(req);
        return R.ok();
    }

    @PostMapping("/unbind")
    public R<Void> unbind() {
        coupleService.unbind();
        return R.ok();
    }

    @Data
    public static class JoinReq {
        @NotBlank(message = "邀请码不能为空")
        private String code;
    }
}
