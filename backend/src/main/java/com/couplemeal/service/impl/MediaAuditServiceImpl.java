package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.config.StorageProperties;
import com.couplemeal.config.WxConfig;
import com.couplemeal.domain.enums.AuditStatus;
import com.couplemeal.entity.MealCheckin;
import com.couplemeal.entity.MealCheckinImage;
import com.couplemeal.mapper.MealCheckinImageMapper;
import com.couplemeal.mapper.MealCheckinMapper;
import com.couplemeal.service.MediaAuditService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片内容安全实现（见 60-打卡/后端.md §3.3 / §4）。
 *
 * <p>送审：只送原图（缩略图继承结果），参数 media_type=2 / version=2 / scene=1，
 * 成功拿 trace_id，失败抛 WX_API_FAILED 交由调用方决定（打卡仍存图、trace_id 留空）。
 *
 * <p>回调：按 uk_meal_checkin_image_trace 定位单张 → 写单张 audit_status → 重算 meal_checkin 汇总态
 * （含屏蔽→2，仍有审核中→0，全通过→1）。按 trace_id 定值更新，重复推送无副作用。
 *
 * <p>兜底：回调丢失时图片会永久停在审核中，故超时未回写的图统一置为屏蔽（{@link #expirePendingAudit()}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAuditServiceImpl implements MediaAuditService {

    private static final String MEDIA_CHECK_ASYNC_URL =
            "https://api.weixin.qq.com/wxa/media_check_async?access_token=";

    /** 微信建议：通过 */
    private static final String SUGGEST_PASS = "pass";
    /** 补偿扫描的单次上限（避免长事务） */
    private static final int RETRY_LIMIT = 200;
    /** 创建超过 5 分钟仍无 trace_id 的图片才重试 */
    private static final int RETRY_DELAY_MINUTES = 5;
    /** 挂起兜底的单次上限（避免长事务） */
    private static final int PENDING_LIMIT = 200;

    private final MealCheckinMapper mealCheckinMapper;
    private final MealCheckinImageMapper mealCheckinImageMapper;
    private final WxConfig wxConfig;
    private final StorageProperties storageProperties;
    private final RestTemplate wxRestTemplate;

    /** 审核中超过该时长仍未收到回调 → 兜底置为屏蔽 */
    @Value("${couple-meal.audit.pending-timeout-minutes:120}")
    private int pendingTimeoutMinutes;

    /**
     * 异步送审一张图片。
     *
     * @return trace_id；微信返回异常或网络失败抛 WX_API_FAILED
     */
    @Override
    public String submit(String relativeUrl) {
        String url = MEDIA_CHECK_ASYNC_URL + wxConfig.getAccessToken();
        Map<String, Object> body = new HashMap<>();
        // 微信需要外网可访问的完整 URL：库内只存相对路径，此处补前缀（D12）
        body.put("media_url", storageProperties.getUrlPrefix() + relativeUrl);
        body.put("media_type", 2);
        body.put("version", 2);
        body.put("scene", 1);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<MediaCheckResp> resp =
                    wxRestTemplate.postForEntity(url, request, MediaCheckResp.class);
            MediaCheckResp r = resp.getBody();
            if (r == null || r.getErrcode() == null || r.getErrcode() != 0) {
                log.warn("mediaCheckAsync 返回异常 url={} errcode={} errmsg={}",
                        relativeUrl, r == null ? null : r.getErrcode(), r == null ? null : r.getErrmsg());
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            if (r.getTraceId() == null || r.getTraceId().isBlank()) {
                throw new BizException(ErrorCode.WX_API_FAILED);
            }
            return r.getTraceId();
        } catch (RestClientException e) {
            log.error("mediaCheckAsync 调用异常 url={}", relativeUrl, e);
            throw new BizException(ErrorCode.WX_API_FAILED);
        }
    }

    /**
     * 回调处理：定位单张 → 回写状态 → 重算汇总态。trace_id 不存在则忽略（防伪造回调）。
     */
    @Override
    public void handleCallback(String traceId, String suggest) {
        if (traceId == null || traceId.isBlank()) {
            log.warn("审核回调缺少 trace_id");
            return;
        }
        MealCheckinImage image = mealCheckinImageMapper.selectOne(
                new LambdaQueryWrapper<MealCheckinImage>()
                        .eq(MealCheckinImage::getTraceId, traceId));
        if (image == null) {
            log.warn("审核回调 trace_id 不存在，忽略 traceId={}", traceId);
            return;
        }
        int status = SUGGEST_PASS.equalsIgnoreCase(suggest)
                ? AuditStatus.PASSED.getCode()
                : AuditStatus.BLOCKED.getCode();
        mealCheckinImageMapper.update(null, new LambdaUpdateWrapper<MealCheckinImage>()
                .eq(MealCheckinImage::getId, image.getId())
                .set(MealCheckinImage::getAuditStatus, status)
                .set(MealCheckinImage::getAuditedTime, LocalDateTime.now()));
        log.info("审核回调回写 imageId={} traceId={} status={}", image.getId(), traceId, status);
        recalcCheckinAuditStatus(image.getCheckinId());
    }

    /**
     * 重算打卡汇总态：含屏蔽 → 2；仍有审核中 → 0；全部通过 → 1。
     */
    private void recalcCheckinAuditStatus(Long checkinId) {
        List<MealCheckinImage> images = mealCheckinImageMapper.selectList(
                new LambdaQueryWrapper<MealCheckinImage>()
                        .eq(MealCheckinImage::getCheckinId, checkinId));
        boolean hasBlocked = false;
        boolean hasAuditing = false;
        for (MealCheckinImage img : images) {
            Integer s = img.getAuditStatus();
            if (s != null && s == AuditStatus.BLOCKED.getCode()) {
                hasBlocked = true;
            } else if (s == null || s == AuditStatus.AUDITING.getCode()) {
                hasAuditing = true;
            }
        }
        int agg = hasBlocked ? AuditStatus.BLOCKED.getCode()
                : (hasAuditing ? AuditStatus.AUDITING.getCode() : AuditStatus.PASSED.getCode());
        mealCheckinMapper.update(null, new LambdaUpdateWrapper<MealCheckin>()
                .eq(MealCheckin::getId, checkinId)
                .set(MealCheckin::getAuditStatus, agg));
    }

    /**
     * 补偿任务：重新送审 trace_id 为空且创建超过 5 分钟的图片（配套 §3.2「审核挂了也存图」）。
     * 定时任务类不在本模块文件归属内，故直接挂在 Service 上，cron 缺省每 10 分钟一轮。
     */
    @Override
    @Scheduled(cron = "${couple-meal.schedule.audit-retry-cron:0 */10 * * * *}")
    public int retryPendingAudit() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
        List<MealCheckinImage> pendings = mealCheckinImageMapper.selectList(
                new LambdaQueryWrapper<MealCheckinImage>()
                        .isNull(MealCheckinImage::getTraceId)
                        .lt(MealCheckinImage::getCreateTime, before)
                        .orderByAsc(MealCheckinImage::getCreateTime)
                        .last("LIMIT " + RETRY_LIMIT));
        int ok = 0;
        for (MealCheckinImage img : pendings) {
            try {
                String traceId = submit(img.getUrl());
                mealCheckinImageMapper.update(null, new LambdaUpdateWrapper<MealCheckinImage>()
                        .eq(MealCheckinImage::getId, img.getId())
                        .set(MealCheckinImage::getTraceId, traceId));
                ok++;
            } catch (Exception e) {
                // 单张失败不影响其余，下一轮继续
                log.warn("审核补偿失败 imageId={} url={}", img.getId(), img.getUrl(), e);
            }
        }
        if (ok > 0 || !pendings.isEmpty()) {
            log.info("审核补偿：待处理 {} 条，成功回填 {} 条", pendings.size(), ok);
        }
        return ok;
    }

    /**
     * 挂起兜底：已送审（有 trace_id）但创建超过 pending-timeout-minutes 仍停在审核中的图片，
     * 统一置为屏蔽。回调丢失时若不兜底，这张图会永久「审核中」——永不展示、用户无感知。
     *
     * <p>更新条件是 {@code id = ? AND audit_status = 0}：回调若已把状态写成通过/屏蔽，
     * 影响行数为 0，直接跳过，<b>绝不回退终态</b>。单条失败不影响其余，下一轮继续。
     * 独立 cron（缺省每天 03:40），与 retryPendingAudit 的每 10 分钟错开，语义也不混。
     */
    @Override
    @Scheduled(cron = "${couple-meal.schedule.audit-pending-expire-cron:0 40 3 * * *}")
    public int expirePendingAudit() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusMinutes(pendingTimeoutMinutes);
        List<MealCheckinImage> stale = mealCheckinImageMapper.selectList(
                new LambdaQueryWrapper<MealCheckinImage>()
                        .eq(MealCheckinImage::getAuditStatus, AuditStatus.AUDITING.getCode())
                        .lt(MealCheckinImage::getCreateTime, deadline)
                        .orderByAsc(MealCheckinImage::getCreateTime)
                        .last("LIMIT " + PENDING_LIMIT));
        int blocked = 0;
        for (MealCheckinImage img : stale) {
            try {
                // 条件里锁死 audit_status=0，并发的回调先写终态时这里影响 0 行
                int rows = mealCheckinImageMapper.update(null, new LambdaUpdateWrapper<MealCheckinImage>()
                        .eq(MealCheckinImage::getId, img.getId())
                        .eq(MealCheckinImage::getAuditStatus, AuditStatus.AUDITING.getCode())
                        .set(MealCheckinImage::getAuditStatus, AuditStatus.BLOCKED.getCode())
                        .set(MealCheckinImage::getAuditedTime, now));
                if (rows == 0) {
                    continue;
                }
                blocked++;
                log.warn("审核挂起超时兜底：置为屏蔽 imageId={} traceId={} checkinId={}",
                        img.getId(), img.getTraceId(), img.getCheckinId());
                recalcCheckinAuditStatus(img.getCheckinId());
            } catch (Exception e) {
                // 单条失败不影响其余
                log.warn("审核挂起兜底失败 imageId={} traceId={} checkinId={}",
                        img.getId(), img.getTraceId(), img.getCheckinId(), e);
            }
        }
        if (!stale.isEmpty()) {
            log.info("审核挂起兜底：超时审核中 {} 条，处置 {} 条", stale.size(), blocked);
        }
        return blocked;
    }

    /** mediaCheckAsync 响应（微信字段为下划线，需显式映射） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaCheckResp {
        private Integer errcode;
        private String errmsg;

        @JsonProperty("trace_id")
        private String traceId;
    }
}
