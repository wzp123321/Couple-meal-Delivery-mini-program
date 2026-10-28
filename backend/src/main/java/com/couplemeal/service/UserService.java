package com.couplemeal.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料服务：头像上传、资料保存、账号注销（真实删除）。
 *
 * <p>与 {@link AuthService} 的分工：AuthService 只负责「登录换身份 + 签发令牌」，
 * 用户自身的资料与注销删除由本服务承载，避免登录链路臃肿。
 * 契约见 10-关系绑定/后端.md §3.5 / §3.6。
 */
public interface UserService {

    /**
     * 头像上传：类型/大小校验 → 重编码（长边 256、≤100KB、天然剥 EXIF）→ 同步 imgSecCheck 过审 → 落盘并删旧文件。
     * 本步不写库（库由 {@link #saveProfile} 写入），返回相对路径。
     *
     * @return /upload/avatar/{userId}_{ts}.jpg
     */
    String uploadAvatar(MultipartFile file);

    /**
     * 资料保存：昵称（≤64 字 + msgSecCheck）与头像路径（校验归属，4004），两者至少传一个。
     */
    ProfileResp saveProfile(String nickname, String avatar);

    /** 账号注销（真实物理删除，见 后端.md §3.5，前端已做二次确认，服务端不设 confirm 参数） */
    void deleteAccount();

    /** 资料保存响应：返回更新后的昵称与头像，前端就地更新本地用户态 */
    class ProfileResp {
        public String nickname;
        public String avatar;
    }
}
