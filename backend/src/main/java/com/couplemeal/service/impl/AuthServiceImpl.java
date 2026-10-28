package com.couplemeal.service.impl;

import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.common.util.ImageCompressor;
import com.couplemeal.common.util.JwtUtil;
import com.couplemeal.config.StorageProperties;
import com.couplemeal.config.WxConfig;
import com.couplemeal.entity.User;
import com.couplemeal.mapper.CoupleInviteMapper;
import com.couplemeal.mapper.MealCheckinImageMapper;
import com.couplemeal.mapper.MealCheckinMapper;
import com.couplemeal.mapper.UserMapper;
import com.couplemeal.service.AuthService;
import com.couplemeal.service.CoupleService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 鉴权服务实现。
 *
 * <p>流程：code -> code2session 换 openid -> 查/建用户 -> 签发 JWT（login）；
 * 头像上传重编码+审核+落盘；资料保存（昵称文本审核 + 头像归属校验）；账号注销真实删除。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 头像类型白名单（声明层面；实际重编码后统一落 JPEG） */
    private static final java.util.Set<String> ALLOWED_EXT =
            java.util.Set.of("jpg", "jpeg", "png", "webp");
    private static final java.util.Set<String> ALLOWED_CT = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final UserMapper userMapper;
    private final CoupleInviteMapper coupleInviteMapper;
    private final MealCheckinMapper mealCheckinMapper;
    private final MealCheckinImageMapper mealCheckinImageMapper;
    private final CoupleService coupleService;
    private final JwtUtil jwtUtil;
    private final WxConfig wxConfig;
    private final StorageProperties storageProperties;
    private final RestTemplate wxRestTemplate;

    public AuthServiceImpl(UserMapper userMapper, CoupleInviteMapper coupleInviteMapper,
                           MealCheckinMapper mealCheckinMapper,
                           MealCheckinImageMapper mealCheckinImageMapper,
                           CoupleService coupleService, JwtUtil jwtUtil,
                           WxConfig wxConfig, StorageProperties storageProperties,
                           RestTemplate wxRestTemplate) {
        this.userMapper = userMapper;
        this.coupleInviteMapper = coupleInviteMapper;
        this.mealCheckinMapper = mealCheckinMapper;
        this.mealCheckinImageMapper = mealCheckinImageMapper;
        this.coupleService = coupleService;
        this.jwtUtil = jwtUtil;
        this.wxConfig = wxConfig;
        this.storageProperties = storageProperties;
        this.wxRestTemplate = wxRestTemplate;
    }

    // ===================== 登录 =====================

    @Override
    public LoginResp login(String code, Boolean agreedPrivacy) {
        if (agreedPrivacy == null || !agreedPrivacy) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        String openid = code2session(code);
        User user = userMapper.selectByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setId(IdUtil.nextId());
            user.setOpenid(openid);
            userMapper.insert(user);
            log.info("新建微信用户 openid={}", openid);
        }
        LoginResp resp = new LoginResp();
        resp.token = jwtUtil.generate(user.getId());
        resp.userId = user.getId();
        resp.nickname = user.getNickname();
        resp.avatar = user.getAvatar();
        resp.hasCouple = coupleService.resolveCoupleId(user.getId()) != null;
        return resp;
    }

    /** 调用微信 code2session，成功返回 openid，失败抛 WX_API_FAILED */
    private String code2session(String code) {
        String url = String.format(CODE2SESSION_URL,
                wxConfig.getAppid(), wxConfig.getSecret(), code);
        try {
            Code2SessionResp resp = wxRestTemplate.getForObject(url, Code2SessionResp.class);
            if (resp == null) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            if (resp.getErrcode() != null && resp.getErrcode() != 0) {
                log.warn("code2session 失败 errcode={} errmsg={}", resp.getErrcode(), resp.getErrmsg());
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            if (resp.getOpenid() == null) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            return resp.getOpenid();
        } catch (RestClientException e) {
            log.error("code2session 调用异常", e);
            throw new BizException(ErrorCode.WX_API_FAILED);
        }
    }

    @Override
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    // ===================== 头像上传 =====================

    @Override
    public String uploadAvatar(MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择图片");
        }
        if (file.getSize() > storageProperties.getAvatarMaxRawSize()) {
            throw new BizException(ErrorCode.IMAGE_TOO_LARGE);
        }
        if (!isAllowedType(file)) {
            throw new BizException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
        }
        // 重编码：长边 256、JPEG0.8、循环降质 ≤100KB（天然剥 EXIF）
        byte[] bytes;
        try {
            bytes = ImageCompressor.compressAvatar(
                    file.getInputStream(),
                    storageProperties.getAvatarEdge(),
                    storageProperties.getAvatarMaxSize());
        } catch (IOException e) {
            log.error("头像重编码失败 userId={}", userId, e);
            throw new BizException(ErrorCode.SYS_ERROR);
        }
        // 同步审核：不过审直接 4003 且不落盘
        boolean pass;
        try {
            pass = wxConfig.imgSecCheck(bytes);
        } catch (BizException be) {
            // 微信接口异常（非内容问题）→ 5001，让用户重试
            throw be;
        }
        if (!pass) {
            throw new BizException(ErrorCode.AVATAR_AUDIT_FAILED);
        }
        // 落盘 + 删旧（旧路径从 user.avatar 读）
        String fileName = userId + "_" + LocalDateTime.now().format(TS_FMT) + ".jpg";
        String relPath = "/upload/avatar/" + fileName;
        Path abs = Paths.get(storageProperties.getBaseDir(), "avatar", fileName);
        try {
            Files.createDirectories(abs.getParent());
            User old = userMapper.selectById(userId);
            if (old != null && old.getAvatar() != null) {
                deleteFileQuietly(storageProperties.resolveAbsolute(old.getAvatar()));
            }
            Files.write(abs, bytes);
        } catch (IOException e) {
            log.error("头像落盘失败 userId={} path={}", userId, abs, e);
            throw new BizException(ErrorCode.SYS_ERROR);
        }
        return relPath;
    }

    private boolean isAllowedType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ALLOWED_CT.contains(ct.toLowerCase())) {
            return true;
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String ext = name.substring(dot + 1).toLowerCase();
                return ALLOWED_EXT.contains(ext);
            }
        }
        return false;
    }

    // ===================== 资料保存 =====================

    @Override
    public ProfileResp saveProfile(String nickname, String avatar) {
        Long userId = UserContext.getUserId();
        boolean hasNick = nickname != null && !nickname.isBlank();
        boolean hasAvatar = avatar != null && !avatar.isBlank();
        if (!hasNick && !hasAvatar) {
            throw new BizException(ErrorCode.PARAM_INVALID, "昵称和头像至少传一个");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        var uw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, userId);
        if (hasNick) {
            if (nickname.length() > 64) {
                throw new BizException(ErrorCode.NICKNAME_INVALID, "昵称不能超过 64 个字");
            }
            if (!wxConfig.msgSecCheck(nickname, user.getOpenid())) {
                throw new BizException(ErrorCode.NICKNAME_INVALID, "昵称包含敏感内容");
            }
            uw.set(User::getNickname, nickname);
        }
        if (hasAvatar) {
            validateAvatarOwnership(avatar, userId);
            uw.set(User::getAvatar, avatar);
        }
        userMapper.update(null, uw);

        User updated = userMapper.selectById(userId);
        ProfileResp resp = new ProfileResp();
        resp.nickname = updated.getNickname();
        resp.avatar = updated.getAvatar();
        return resp;
    }

    /** 头像路径校验：前缀 /upload/avatar/ + 文件存在 + 文件名含本人 userId，否则 4004 */
    private void validateAvatarOwnership(String avatar, Long userId) {
        if (!avatar.startsWith("/upload/avatar/")) {
            throw new BizException(ErrorCode.AVATAR_INVALID);
        }
        String fileName = avatar.substring(avatar.lastIndexOf('/') + 1);
        if (!fileName.contains(String.valueOf(userId))) {
            throw new BizException(ErrorCode.AVATAR_INVALID);
        }
        Path abs = Paths.get(storageProperties.resolveAbsolute(avatar));
        if (!Files.exists(abs)) {
            throw new BizException(ErrorCode.AVATAR_INVALID);
        }
    }

    // ===================== 账号注销 =====================

    @Override
    public void deleteAccount() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        // 先库后文件：DB 在一个事务内处理完，提交后删文件
        List<String> files = doDeleteInTx(userId);
        for (String f : files) {
            deleteFileQuietly(f);
        }
        coupleService.evictCache(userId);
    }

    /**
     * 注销的 DB 部分（一个事务，先库后文件）：
     * 1) 有有效关系先逻辑解绑；2) 删本人打卡图片行+收集路径；3) 作废本人未用邀请码；4) 物理删 user。
     */
    @Transactional
    public List<String> doDeleteInTx(Long userId) {
        List<String> files = new ArrayList<>();

        // 1) 有有效关系先解绑（逻辑删除，保留对方历史）
        if (coupleService.resolveCoupleId(userId) != null) {
            coupleService.unbind();
        }

        // 2) 本人打卡图片：收集路径 → 删子表 → 删主表
        List<Map<String, Object>> imgPaths = mealCheckinImageMapper.selectImagePathsByUser(userId);
        for (Map<String, Object> m : imgPaths) {
            if (m.get("url") != null) {
                files.add(storageProperties.resolveAbsolute((String) m.get("url")));
            }
            if (m.get("thumb_url") != null) {
                files.add(storageProperties.resolveAbsolute((String) m.get("thumb_url")));
            }
        }
        mealCheckinImageMapper.deleteImagesByUser(userId);
        mealCheckinMapper.deleteByUser(userId);

        // 3) 作废本人未使用的邀请码（防注销后码还能绑上）
        coupleInviteMapper.voidByCreator(userId, LocalDateTime.now());

        // 4) 物理删除 user（openid 即身份标识，必须真删）
        User user = userMapper.selectById(userId);
        if (user != null && user.getAvatar() != null) {
            files.add(storageProperties.resolveAbsolute(user.getAvatar()));
        }
        userMapper.deleteById(userId);

        return files;
    }

    private void deleteFileQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            // 文件删除失败不能回滚事务，记 WARN，可补脚本（设计 §3.5）
            log.warn("注销删除文件失败 path={}", path, e);
        }
    }

    /** code2session 响应（忽略未映射字段） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Code2SessionResp {
        private String openid;
        private String sessionKey;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }
}
