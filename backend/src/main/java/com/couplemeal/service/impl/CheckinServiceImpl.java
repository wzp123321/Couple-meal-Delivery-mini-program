package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.common.util.ImageCompressor;
import com.couplemeal.config.StorageProperties;
import com.couplemeal.controller.CheckinController;
import com.couplemeal.domain.enums.AuditStatus;
import com.couplemeal.domain.enums.CheckinRating;
import com.couplemeal.domain.enums.NotifyType;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.MealCheckin;
import com.couplemeal.entity.MealCheckinImage;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.mapper.MealCheckinImageMapper;
import com.couplemeal.mapper.MealCheckinMapper;
import com.couplemeal.mapper.MealPlanMapper;
import com.couplemeal.service.CheckinService;
import com.couplemeal.service.MediaAuditService;
import com.couplemeal.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 打卡服务实现（见 60-打卡/后端.md）。
 *
 * <p>要点：
 *  - 两步上传：第一步只出图（重编码+剥 EXIF），第二步提交时才落库并送审；
 *  - 只有接收方（orderer）能打卡，窗口为交付当天 10:00 ~ 次日 02:00；
 *  - 审核接口挂掉也要先存图（trace_id 留空等补偿任务），不能丢用户照片；
 *  - 打卡不加爱心值，只发一条 CHECKED_IN 通知；
 *  - 展示只返回 audit_status=1 的图片（未过审的 URL 不出现在响应里）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    /** 库内图片路径前缀（D12：只存相对路径） */
    private static final String UPLOAD_PREFIX = "/upload/";
    /** 上传目录按月分目录 */
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    /** 相册游标格式 */
    private static final DateTimeFormatter CURSOR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 月份分组标题格式 */
    private static final DateTimeFormatter MONTH_TITLE_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final int MAX_CONTENT_LEN = 100;
    private static final int MAX_REPLY_LEN = 50;
    private static final int MAX_IMAGE_COUNT = 2;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 30;

    /** 打卡窗口：交付当天 10:00 起，次日 02:00 止（与 PlanExpireJob 02:30 错开） */
    private static final int WINDOW_START_HOUR = 10;
    private static final int WINDOW_END_HOUR = 2;

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPE = Set.of("image/jpeg", "image/png", "image/webp");

    private final MealCheckinMapper mealCheckinMapper;
    private final MealCheckinImageMapper mealCheckinImageMapper;
    private final MealPlanMapper mealPlanMapper;
    private final MediaAuditService mediaAuditService;
    private final NotifyService notifyService;
    private final StorageProperties storageProperties;

    // ===================== 第一步：上传 =====================

    /**
     * 上传单张照片：类型与大小校验（4001/4002）→ 重编码两档 + 剥 EXIF → 落盘 /upload/yyyyMM/。
     * 缩略图失败则整体失败（5001），不允许只有原图入库（§3.1）。
     */
    @Override
    public CheckinController.UploadResp upload(MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择图片");
        }
        if (file.getSize() > storageProperties.getMaxRawSize()) {
            throw new BizException(ErrorCode.IMAGE_TOO_LARGE);
        }
        if (!isAllowedType(file)) {
            throw new BizException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传图片失败 userId={}", userId, e);
            throw new BizException(ErrorCode.SYS_ERROR);
        }

        // 重编码两档：原图长边 1080 / ≤500KB，缩略图长边 320 / ≤60KB。
        // 走 ImageCompressor 重编码即天然剥离 EXIF（含 GPS），两档都不带元数据。
        byte[] originBytes;
        try {
            originBytes = ImageCompressor.compressAvatar(new ByteArrayInputStream(raw),
                    storageProperties.getMaxEdge(), storageProperties.getMaxOutputSize());
        } catch (IOException e) {
            log.warn("打卡原图重编码失败 userId={}", userId, e);
            throw new BizException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
        }
        byte[] thumbBytes;
        try {
            thumbBytes = ImageCompressor.compressAvatar(new ByteArrayInputStream(raw),
                    storageProperties.getThumbEdge(), storageProperties.getThumbMaxSize());
        } catch (IOException e) {
            log.error("打卡缩略图生成失败 userId={}", userId, e);
            throw new BizException(ErrorCode.WX_API_FAILED);
        }

        String month = LocalDateTime.now().format(MONTH_FMT);
        String name = UUID.randomUUID().toString().replace("-", "");
        String url = UPLOAD_PREFIX + month + "/" + name + ".jpg";
        String thumbUrl = UPLOAD_PREFIX + month + "/" + name + "_thumb.jpg";
        try {
            writeFile(url, originBytes);
            writeFile(thumbUrl, thumbBytes);
        } catch (IOException e) {
            log.error("打卡图片落盘失败 userId={} url={}", userId, url, e);
            throw new BizException(ErrorCode.SYS_ERROR);
        }

        CheckinController.UploadResp resp = new CheckinController.UploadResp();
        resp.setUrl(url);
        resp.setThumbUrl(thumbUrl);
        return resp;
    }

    private boolean isAllowedType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ALLOWED_CONTENT_TYPE.contains(ct.toLowerCase())) {
            return true;
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                return ALLOWED_EXT.contains(name.substring(dot + 1).toLowerCase());
            }
        }
        return false;
    }

    private void writeFile(String relativePath, byte[] data) throws IOException {
        Path abs = Paths.get(storageProperties.resolveAbsolute(relativePath));
        Files.createDirectories(abs.getParent());
        Files.write(abs, data);
    }

    // ===================== 第二步：提交 =====================

    /**
     * 提交打卡：权限（3004）/ 状态（3010）/ 窗口（3003）/ 图片数（3002）校验通过后落库，
     * 逐张送审（失败仅留空 trace_id），最后发一条「已打卡」通知。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(CheckinController.SubmitReq req) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        if (req == null || req.getPlanId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少约定 id");
        }

        // 跨模块只读校验：约定必须属于本关系，且状态已交付/已完成
        MealPlan plan = mealPlanMapper.selectById(req.getPlanId());
        if (plan == null || !coupleId.equals(plan.getCoupleId())) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        if (!me.equals(plan.getOrdererId())) {
            throw new BizException(ErrorCode.CHECKIN_ONLY_RECEIVER);
        }
        if (plan.getStatus() == null
                || (plan.getStatus() != PlanStatus.DELIVERED.getCode()
                && plan.getStatus() != PlanStatus.COMPLETED.getCode())) {
            throw new BizException(ErrorCode.CHECKIN_NOT_DELIVERED);
        }
        assertInWindow(plan);

        List<String> urls = req.getImages();
        if (urls == null || urls.isEmpty() || urls.size() > MAX_IMAGE_COUNT) {
            throw new BizException(ErrorCode.CHECKIN_IMAGE_COUNT);
        }
        CheckinRating rating = parseRating(req.getRating());
        String content = trimToNull(req.getContent());
        if (content != null && content.length() > MAX_CONTENT_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "点评不能超过 " + MAX_CONTENT_LEN + " 个字");
        }

        // 逐张校验路径合法且两档文件真实存在，缩略图按 x.jpg → x_thumb.jpg 推导
        List<String[]> imagePairs = new ArrayList<>(urls.size());
        for (String url : urls) {
            imagePairs.add(new String[]{url, resolveThumbUrl(url)});
        }

        MealCheckin checkin = new MealCheckin();
        checkin.setId(IdUtil.nextId());
        checkin.setPlanId(plan.getId());
        checkin.setCoupleId(coupleId);
        checkin.setUserId(me);
        checkin.setRating(rating.getCode());
        checkin.setContent(content);
        checkin.setAuditStatus(AuditStatus.AUDITING.getCode());
        try {
            mealCheckinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            // uk_meal_checkin_plan：一个约定只打一次卡
            throw new BizException(ErrorCode.ALREADY_CHECKED);
        }

        for (int i = 0; i < imagePairs.size(); i++) {
            String url = imagePairs.get(i)[0];
            String thumb = imagePairs.get(i)[1];
            // 只审原图，缩略图继承原图结果；审核接口失败仍存图，trace_id 留空等补偿任务
            String traceId = null;
            try {
                traceId = mediaAuditService.submit(url);
            } catch (Exception e) {
                log.warn("图片送审失败，先存图等待补偿 checkinId={} url={}", checkin.getId(), url, e);
            }
            MealCheckinImage image = new MealCheckinImage();
            image.setId(IdUtil.nextId());
            image.setCheckinId(checkin.getId());
            image.setUrl(url);
            image.setThumbUrl(thumb);
            image.setSeq(i);
            image.setAuditStatus(AuditStatus.AUDITING.getCode());
            image.setTraceId(traceId);
            mealCheckinImageMapper.insert(image);
        }

        // 打卡不额外加爱心值（PRD Q6），只发一条「已打卡」通知给带饭方
        Long receiverId = plan.getCarrierId();
        if (receiverId != null) {
            String text = rating.getDesc() + (content == null ? "" : "：" + content);
            notifyService.push(coupleId, receiverId, plan.getId(), NotifyType.CHECKED_IN, "对方已打卡", text);
        }
        return checkin.getId();
    }

    /** 打卡窗口：交付当天 10:00 ~ 次日 02:00（起点不是 00:00，见 §3.2 备注） */
    private void assertInWindow(MealPlan plan) {
        LocalDate base = plan.getDeliveredTime() != null
                ? plan.getDeliveredTime().toLocalDate()
                : plan.getPlanDate();
        if (base == null) {
            throw new BizException(ErrorCode.CHECKIN_NOT_DELIVERED);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = base.atTime(WINDOW_START_HOUR, 0);
        LocalDateTime end = base.plusDays(1).atTime(WINDOW_END_HOUR, 0);
        if (now.isBefore(start) || !now.isBefore(end)) {
            throw new BizException(ErrorCode.CHECKIN_WINDOW_CLOSED);
        }
    }

    private CheckinRating parseRating(Integer rating) {
        if (rating == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "说说今天味道怎么样");
        }
        return CheckinRating.of(rating);
    }

    /** 校验原图路径合法且落盘存在，并推导缩略图路径（x.jpg → x_thumb.jpg） */
    private String resolveThumbUrl(String url) {
        if (url == null || !url.startsWith(UPLOAD_PREFIX) || url.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片路径不合法");
        }
        int dot = url.lastIndexOf('.');
        if (dot <= url.lastIndexOf('/')) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片路径不合法");
        }
        String thumb = url.substring(0, dot) + "_thumb.jpg";
        if (!Files.exists(Paths.get(storageProperties.resolveAbsolute(url)))
                || !Files.exists(Paths.get(storageProperties.resolveAbsolute(thumb)))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片不存在，请重新上传");
        }
        return thumb;
    }

    // ===================== 修改与回应 =====================

    /**
     * 修改评价与点评：仅限打卡人本人在打卡当天（不提供删除，PRD 约定）。
     */
    @Override
    public void update(CheckinController.UpdateReq req) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        MealCheckin checkin = loadOwnCheckin(req == null ? null : req.getCheckinId(), coupleId, me);

        if (checkin.getCreateTime() == null
                || !checkin.getCreateTime().toLocalDate().equals(LocalDate.now())) {
            throw new BizException(ErrorCode.CHECKIN_WINDOW_CLOSED);
        }
        CheckinRating rating = parseRating(req.getRating());
        String content = trimToNull(req.getContent());
        if (content != null && content.length() > MAX_CONTENT_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "点评不能超过 " + MAX_CONTENT_LEN + " 个字");
        }
        LambdaUpdateWrapper<MealCheckin> uw = new LambdaUpdateWrapper<MealCheckin>()
                .eq(MealCheckin::getId, checkin.getId())
                .set(MealCheckin::getRating, rating.getCode())
                .set(MealCheckin::getContent, content);
        mealCheckinMapper.update(null, uw);
    }

    /**
     * 带饭方回应：≤50 字，覆盖式更新（一条打卡只有一条回应）。
     * MealCheckin 实体未映射 reply 三列，此处用列名直接更新（不改动公共实体）。
     */
    @Override
    public void reply(CheckinController.ReplyReq req) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        if (req == null || req.getCheckinId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少打卡 id");
        }
        MealCheckin checkin = mealCheckinMapper.selectById(req.getCheckinId());
        if (checkin == null || !coupleId.equals(checkin.getCoupleId())) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        if (me.equals(checkin.getUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能回应对方的打卡");
        }
        String reply = trimToNull(req.getReply());
        if (reply == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回应不能为空");
        }
        if (reply.length() > MAX_REPLY_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回应不能超过 " + MAX_REPLY_LEN + " 个字");
        }
        UpdateWrapper<MealCheckin> uw = new UpdateWrapper<MealCheckin>()
                .eq("id", checkin.getId())
                .set("reply", reply)
                .set("reply_by", me)
                .set("reply_time", LocalDateTime.now());
        mealCheckinMapper.update(null, uw);
    }

    private MealCheckin loadOwnCheckin(Long checkinId, Long coupleId, Long me) {
        if (checkinId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少打卡 id");
        }
        MealCheckin checkin = mealCheckinMapper.selectById(checkinId);
        if (checkin == null || !coupleId.equals(checkin.getCoupleId()) || !me.equals(checkin.getUserId())) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        return checkin;
    }

    // ===================== 相册 =====================

    /**
     * 饭桌相册：本关系下打卡按 create_time 倒序游标分页、按月分组。
     * 可见性：本人看全部（含审核中/被屏蔽的整条），对方只看已通过的（audit_status=1）；
     * 图片一律只返回 audit_status=1 的单张（§3.4：未过审的 URL 不得出现在响应里）。
     */
    @Override
    public CheckinController.GalleryResp gallery(String cursor, Integer size) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        int pageSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        QueryWrapper<MealCheckin> qw = new QueryWrapper<>();
        // MealCheckin 实体未映射 reply 三列，用别名直取（列名 → 驼峰别名，避免下划线映射差异）
        qw.select("id", "plan_id AS planId", "couple_id AS coupleId", "user_id AS userId",
                        "rating", "content", "reply", "reply_time AS replyTime",
                        "audit_status AS auditStatus", "create_time AS createTime")
                .eq("couple_id", coupleId);
        LocalDateTime cursorTime = parseCursor(cursor);
        if (cursorTime != null) {
            qw.lt("create_time", cursorTime);
        }
        qw.orderByDesc("create_time").last("LIMIT " + (pageSize + 1L));

        List<Map<String, Object>> rows = mealCheckinMapper.selectMaps(qw);
        boolean hasMore = rows.size() > pageSize;
        List<Map<String, Object>> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        List<Long> visibleIds = new ArrayList<>();
        List<CheckinController.GalleryCard> cards = new ArrayList<>();
        for (Map<String, Object> row : pageRows) {
            int auditStatus = intVal(row, "auditStatus", 0);
            boolean mine = me.equals(longVal(row, "userId"));
            // 对方的打卡必须全部通过才进相册；本人的整条始终可见（图片仍只取已过审的）
            if (!mine && auditStatus != AuditStatus.PASSED.getCode()) {
                continue;
            }
            Long checkinId = longVal(row, "id");
            visibleIds.add(checkinId);
            CheckinController.GalleryCard card = new CheckinController.GalleryCard();
            card.setCheckinId(checkinId);
            card.setPlanId(longVal(row, "planId"));
            card.setMine(mine);
            card.setRating(intVal(row, "rating", 0));
            card.setContent(strVal(row, "content"));
            card.setReply(strVal(row, "reply"));
            card.setAuditStatus(auditStatus);
            card.setCreateTime(timeVal(row, "createTime"));
            card.setImages(new ArrayList<>());
            cards.add(card);
        }

        // 批量取已过审图片，按 seq 升序；未过审/审核中的图片不返回
        Map<Long, List<CheckinController.GalleryImage>> imageMap = new HashMap<>();
        if (!visibleIds.isEmpty()) {
            List<MealCheckinImage> images = mealCheckinImageMapper.selectList(
                    new LambdaQueryWrapper<MealCheckinImage>()
                            .in(MealCheckinImage::getCheckinId, visibleIds)
                            .eq(MealCheckinImage::getAuditStatus, AuditStatus.PASSED.getCode())
                            .orderByAsc(MealCheckinImage::getSeq));
            for (MealCheckinImage img : images) {
                CheckinController.GalleryImage gi = new CheckinController.GalleryImage();
                gi.setUrl(img.getUrl());
                gi.setThumbUrl(img.getThumbUrl());
                imageMap.computeIfAbsent(img.getCheckinId(), k -> new ArrayList<>()).add(gi);
            }
        }
        for (CheckinController.GalleryCard card : cards) {
            List<CheckinController.GalleryImage> list = imageMap.get(card.getCheckinId());
            card.setImages(list == null ? new ArrayList<>() : list);
        }

        // 按自然月分组（结果已按时间倒序，遇月份变化即开新组）
        Map<String, CheckinController.MonthGroup> groups = new LinkedHashMap<>();
        for (CheckinController.GalleryCard card : cards) {
            if (card.getCreateTime() == null) {
                continue;
            }
            String month = card.getCreateTime().format(MONTH_TITLE_FMT);
            CheckinController.MonthGroup group = groups.computeIfAbsent(month, m -> {
                CheckinController.MonthGroup g = new CheckinController.MonthGroup();
                g.setMonth(m);
                g.setCards(new ArrayList<>());
                return g;
            });
            group.getCards().add(card);
        }

        CheckinController.GalleryResp resp = new CheckinController.GalleryResp();
        resp.setMonths(new ArrayList<>(groups.values()));
        resp.setHasMore(hasMore);
        LocalDateTime last = pageRows.isEmpty() ? null : timeVal(pageRows.get(pageRows.size() - 1), "createTime");
        resp.setNextCursor(hasMore && last != null ? last.format(CURSOR_FMT) : null);
        return resp;
    }

    private LocalDateTime parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(cursor.trim(), CURSOR_FMT);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "游标格式有误");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Long longVal(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : ((Number) v).longValue();
    }

    private static int intVal(Map<String, Object> row, String key, int def) {
        Object v = row.get(key);
        return v == null ? def : ((Number) v).intValue();
    }

    private static String strVal(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static LocalDateTime timeVal(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime t) {
            return t;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(v), CURSOR_FMT);
    }
}
