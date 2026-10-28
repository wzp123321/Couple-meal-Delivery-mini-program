package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.AvatarStorage;
import com.couplemeal.common.util.ImageCompressor;
import com.couplemeal.config.StorageProperties;
import com.couplemeal.config.WxConfig;
import com.couplemeal.entity.User;
import com.couplemeal.mapper.CoupleInviteMapper;
import com.couplemeal.mapper.MealCheckinImageMapper;
import com.couplemeal.mapper.MealCheckinMapper;
import com.couplemeal.mapper.UserMapper;
import com.couplemeal.service.CoupleService;
import com.couplemeal.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户资料服务实现：头像上传（D16）、资料保存、账号注销（真实删除，PRD §13 合规 P0）。
 *
 * <p>三个方法都不接收 coupleId / userId 入参，用户身份一律从 {@link UserContext} 取（数据隔离铁律）。
 *
 * <p>注销顺序严格「先库后文件」：DB 在一个事务里改完，文件删除挂到事务提交之后执行
 * （{{@link TransactionSynchronization#afterCommit()}}），避免事务回滚了文件却已删、用户照片消失。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /** 昵称长度上限（与 user.nickname VARCHAR(64) 对齐） */
    private static final int NICKNAME_MAX = 64;

    private static final Set<String> ALLOWED_CONTENT_TYPE = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    private final UserMapper userMapper;
    private final CoupleInviteMapper coupleInviteMapper;
    private final MealCheckinMapper mealCheckinMapper;
    private final MealCheckinImageMapper mealCheckinImageMapper;
    private final CoupleService coupleService;
    private final WxConfig wxConfig;
    private final StorageProperties storageProperties;
    private final AvatarStorage avatarStorage;

    public UserServiceImpl(UserMapper userMapper, CoupleInviteMapper coupleInviteMapper,
                           MealCheckinMapper mealCheckinMapper,
                           MealCheckinImageMapper mealCheckinImageMapper,
                           CoupleService coupleService, WxConfig wxConfig,
                           StorageProperties storageProperties, AvatarStorage avatarStorage) {
        this.userMapper = userMapper;
        this.coupleInviteMapper = coupleInviteMapper;
        this.mealCheckinMapper = mealCheckinMapper;
        this.mealCheckinImageMapper = mealCheckinImageMapper;
        this.coupleService = coupleService;
        this.wxConfig = wxConfig;
        this.storageProperties = storageProperties;
        this.avatarStorage = avatarStorage;
    }

    // ===================== 头像上传 =====================

    @Override
    public String uploadAvatar(MultipartFile file) {
        Long userId = currentUserId();
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要上传的头像");
        }
        if (file.getSize() > storageProperties.getAvatarMaxRawSize()) {
            throw new BizException(ErrorCode.IMAGE_TOO_LARGE);
        }
        if (!isAllowedImage(file)) {
            throw new BizException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
        }
        // 重编码：长边 256、JPEG 质量 0.8 循环降质到 ≤100KB；重编码天然剥离 EXIF（含定位信息）
        byte[] avatar;
        try (InputStream in = file.getInputStream()) {
            avatar = ImageCompressor.compressAvatar(in,
                    storageProperties.getAvatarEdge(), storageProperties.getAvatarMaxSize());
        } catch (IOException e) {
            // 走到这里基本只有「文件不是合法图片」一种可能
            log.warn("头像重编码失败 userId={}", userId, e);
            throw new BizException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
        }
        // 同步内容审核：未通过直接 4003 且不落盘（头像是 UGC，微信要求开发者自审）
        if (!wxConfig.imgSecCheck(avatar)) {
            throw new BizException(ErrorCode.AVATAR_AUDIT_FAILED);
        }
        User current = userMapper.selectById(userId);
        if (current == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        String relativePath;
        try {
            relativePath = avatarStorage.save(userId, avatar);
        } catch (IOException e) {
            log.error("头像落盘失败 userId={}", userId, e);
            throw new BizException(ErrorCode.SYS_ERROR);
        }
        // 换头像删旧文件。同一秒内重复上传会撞出同名文件，此时绝不能删
        String old = current.getAvatar();
        if (old != null && !old.equals(relativePath)) {
            avatarStorage.deleteQuietly(old);
        }
        return relativePath;
    }

    private boolean isAllowedImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && ALLOWED_CONTENT_TYPE.contains(contentType.toLowerCase())) {
            return true;
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                return ALLOWED_EXT.contains(name.substring(dot + 1).toLowerCase());
            }
        }
        return false;
    }

    // ===================== 资料保存 =====================

    @Override
    public ProfileResp saveProfile(String nickname, String avatar) {
        Long userId = currentUserId();
        boolean hasNickname = nickname != null && !nickname.isBlank();
        boolean hasAvatar = avatar != null && !avatar.isBlank();
        if (!hasNickname && !hasAvatar) {
            throw new BizException(ErrorCode.PARAM_INVALID, "昵称和头像至少传一个");
        }
        User current = userMapper.selectById(userId);
        if (current == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (hasNickname) {
            String nick = nickname.trim();
            if (nick.length() > NICKNAME_MAX) {
                throw new BizException(ErrorCode.NICKNAME_INVALID, "昵称不能超过 64 个字");
            }
            // 昵称同属 UGC，只审图不审字照样会被微信驳回（msgSecCheck 失败统一 5001）
            if (!wxConfig.msgSecCheck(nick, current.getOpenid())) {
                throw new BizException(ErrorCode.NICKNAME_INVALID, "昵称包含敏感内容");
            }
            uw.set(User::getNickname, nick);
        }
        if (hasAvatar) {
            if (!avatarStorage.belongsTo(avatar.trim(), userId)) {
                throw new BizException(ErrorCode.AVATAR_INVALID);
            }
            uw.set(User::getAvatar, avatar.trim());
        }
        userMapper.update(null, uw);

        User updated = userMapper.selectById(userId);
        ProfileResp resp = new ProfileResp();
        resp.nickname = updated.getNickname();
        resp.avatar = updated.getAvatar();
        return resp;
    }

    // ===================== 账号注销 =====================

    @Override
    @Transactional
    public void deleteAccount() {
        Long userId = currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        // 先库（本事务）后文件（afterCommit）：openid / 打卡照这些身份数据必须真删，一个事务内搞定
        List<String> toDelete = new ArrayList<>();

        // 1) 存在有效关系先按 §3.4 逻辑解绑——另一方还要看自己的历史，couple 行保留
        if (coupleService.resolveCoupleId(userId) != null) {
            coupleService.unbind();
        }
        // 2) 本人上传的打卡图片：先收集路径，行记录随后删除
        collectCheckinImageFiles(userId, toDelete);
        mealCheckinImageMapper.deleteImagesByUser(userId);
        mealCheckinMapper.deleteByUser(userId);
        // 3) 作废本人未使用的邀请码，防注销后旧码还能被别人绑上
        coupleInviteMapper.voidByCreator(userId, LocalDateTime.now());
        // 4) 物理删除 user 行（openid 就是身份标识，留着等于没删）
        if (user.getAvatar() != null) {
            toDelete.add(avatarStorage.toAbsolute(user.getAvatar()));
        }
        userMapper.deleteById(userId);
        coupleService.evictCache(userId);

        deleteFilesAfterCommit(toDelete);
    }

    /** 收集本人打卡图片（原图 + 缩略图）的磁盘绝对路径 */
    private void collectCheckinImageFiles(Long userId, List<String> sink) {
        for (Map<String, Object> row : mealCheckinImageMapper.selectImagePathsByUser(userId)) {
            addIfPresent(sink, (String) row.get("url"));
            addIfPresent(sink, (String) row.get("thumb_url"));
        }
    }

    private void addIfPresent(List<String> sink, String relativePath) {
        if (relativePath != null && !relativePath.isBlank()) {
            // 打卡图位于 /upload/yyyyMM/，与头像不同目录，直接按通用相对路径解析
            sink.add(storageProperties.resolveAbsolute(relativePath));
        }
    }

    /**
     * 事务提交后再删文件。文件删除是幂等清理，单张失败只记 WARN，
     * 绝不能因为 IO 失败让用户注销不掉（可用补脚本重跑）。
     */
    private void deleteFilesAfterCommit(List<String> files) {
        if (files.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    files.forEach(UserServiceImpl.this::deleteQuietly);
                }
            });
        } else {
            files.forEach(this::deleteQuietly);
        }
    }

    private void deleteQuietly(String absolutePath) {
        try {
            Files.deleteIfExists(Paths.get(absolutePath));
        } catch (IOException e) {
            log.warn("注销删除文件失败 path={}", absolutePath, e);
        }
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        return userId;
    }
}
