package com.couplemeal.service;

import com.couplemeal.entity.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * 鉴权服务：微信登录换身份、签发令牌、头像上传、资料保存、账号注销。
 */
public interface AuthService {

    /**
     * 微信登录：用小程序 code 换取 openid，建/查用户，签发访问令牌。
     * 未同意隐私协议（agreedPrivacy != true）直接拒绝（1001）。
     *
     * @return 登录响应（令牌 + 用户基础信息 + 是否已绑定）
     */
    LoginResp login(String code, Boolean agreedPrivacy);

    /**
     * 头像上传：类型/大小校验 → 重编码 256/JPEG≤100KB → 同步 imgSecCheck 过审 → 落盘（删旧）。
     * 不写库（库由 saveProfile 写入），返回相对路径。
     *
     * @return 头像相对路径 /upload/avatar/{userId}_{ts}.jpg
     */
    String uploadAvatar(MultipartFile file);

    /**
     * 资料保存：昵称（≤64 字 + msgSecCheck）与头像路径（校验归属，4004），至少传一个。
     *
     * @return 更新后的昵称与头像
     */
    ProfileResp saveProfile(String nickname, String avatar);

    /** 账号注销（真实删除，见 后端.md §3.5） */
    void deleteAccount();

    /** 按主键查用户，不存在返回 null（供拦截器做注销后 token 失效校验） */
    User getUserById(Long userId);

    // ===== 响应对象（就近放置，不设 dto 包） =====

    /** 登录响应：契约要求返回 token / userId / nickname / avatar / hasCouple */
    class LoginResp {
        public String token;
        public Long userId;
        public String nickname;
        public String avatar;
        public Boolean hasCouple;
    }

    /** 资料保存响应 */
    class ProfileResp {
        public String nickname;
        public String avatar;
    }
}
