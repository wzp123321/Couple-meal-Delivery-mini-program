package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.UserService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料控制器：头像上传、资料保存、账号注销。
 *
 * <p>路径（context-path=/api）：/api/user/avatar、/api/user/profile、/api/user/delete。
 * 全部走 AuthInterceptor 鉴权（免鉴权的只有 /api/auth/login 等，见 00-全局约定.md §3.5）。
 *
 * <p>userId / coupleId 一律从 UserContext 取，接口不接收身份类入参。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 头像上传：multipart 单张。重编码 + 内容审核通过后才落盘，返回相对路径，本步不写库。
     * 前端随后把该路径随昵称一起提交到 /user/profile（两步走，见 后端.md §3.6）。
     */
    @PostMapping("/avatar")
    public R<String> avatar(@RequestParam("file") MultipartFile file) {
        return R.ok(userService.uploadAvatar(file));
    }

    /**
     * 资料保存：昵称与头像至少传一个。
     */
    @PostMapping("/profile")
    public R<UserService.ProfileResp> profile(@Valid @RequestBody ProfileReq req) {
        return R.ok(userService.saveProfile(req.getNickname(), req.getAvatar()));
    }

    /**
     * 账号注销（真实物理删除，前端已做二次确认，服务端不设 confirm 参数）。
     */
    @PostMapping("/delete")
    public R<Void> delete() {
        userService.deleteAccount();
        return R.ok();
    }

    @Data
    public static class ProfileReq {
        /** 昵称（≤64 字 + msgSecCheck 文本审核），可选 */
        private String nickname;
        /** 头像相对路径（须为本服务上传过且归属本人），可选 */
        private String avatar;
    }
}
