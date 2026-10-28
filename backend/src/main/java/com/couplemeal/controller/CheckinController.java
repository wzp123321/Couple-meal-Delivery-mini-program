package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.CheckinService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打卡控制器（饭桌相册）：两步上传、提交打卡、当天修改、带饭方回应、相册分页。
 *
 * <p>路径（context-path=/api）：/api/checkin/upload、/api/checkin/submit、
 * /api/checkin/update、/api/checkin/reply、/api/checkin/gallery。
 * 全部走 AuthInterceptor 鉴权；coupleId 只从 UserContext 取，入参不接受 coupleId。
 *
 * <p>请求/响应就近定义为静态内部类（不设 dto 包）。
 */
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    /**
     * 第一步：上传单张打卡照片，重编码出两档图后返回相对路径（不写库、不送审）。
     */
    @PostMapping("/upload")
    public R<UploadResp> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(checkinService.upload(file));
    }

    /**
     * 第二步：提交打卡（planId + 1–2 张图片 url + 三档评价 + 点评）。
     */
    @PostMapping("/submit")
    public R<Long> submit(@RequestBody SubmitReq req) {
        return R.ok(checkinService.submit(req));
    }

    /**
     * 修改打卡（仅限打卡当天，不提供删除）。
     */
    @PostMapping("/update")
    public R<Void> update(@RequestBody UpdateReq req) {
        checkinService.update(req);
        return R.ok();
    }

    /**
     * 带饭方回应（≤50 字，覆盖式更新，一条打卡只有一条回应）。
     */
    @PostMapping("/reply")
    public R<Void> reply(@RequestBody ReplyReq req) {
        checkinService.reply(req);
        return R.ok();
    }

    /**
     * 饭桌相册：按创建时间倒序游标分页，按月分组，只返回已过审图片。
     */
    @GetMapping("/gallery")
    public R<GalleryResp> gallery(@RequestParam(required = false) String cursor,
                                  @RequestParam(required = false) Integer size) {
        return R.ok(checkinService.gallery(cursor, size));
    }

    // ===================== 请求 / 响应（就近放置，不设 dto 包） =====================

    /** 上传结果：原图与缩略图的相对路径 */
    @Data
    public static class UploadResp {
        /** 原图 /upload/yyyyMM/{uuid}.jpg（长边 1080、≤500KB） */
        private String url;
        /** 缩略图 /upload/yyyyMM/{uuid}_thumb.jpg（长边 320、≤60KB），列表专用 */
        private String thumbUrl;
    }

    /** 提交打卡入参 */
    @Data
    public static class SubmitReq {
        private Long planId;
        /** 1–2 张原图相对路径（由 /upload 返回） */
        private List<String> images;
        /** 三档评价：1还差点意思 2好吃 3香，必选 */
        private Integer rating;
        /** 点评，≤100 字，可选 */
        private String content;
    }

    /** 修改打卡入参 */
    @Data
    public static class UpdateReq {
        private Long checkinId;
        private Integer rating;
        private String content;
    }

    /** 带饭方回应入参 */
    @Data
    public static class ReplyReq {
        private Long checkinId;
        /** ≤50 字 */
        private String reply;
    }

    /** 相册分页响应 */
    @Data
    public static class GalleryResp {
        /** 按月分组，月份倒序 */
        private List<MonthGroup> months;
        /** 下一页游标（最后一条的 createTime），为空表示到底 */
        private String nextCursor;
        private boolean hasMore;
    }

    /** 单月分组 */
    @Data
    public static class MonthGroup {
        /** 月份 yyyy-MM，如 2026-10 */
        private String month;
        private List<GalleryCard> cards;
    }

    /** 相册卡片（图片列表只含已过审图片） */
    @Data
    public static class GalleryCard {
        private Long checkinId;
        private Long planId;
        /** 是否本人打卡（本人可见审核中/屏蔽的整条，对方只看到已通过的） */
        private boolean mine;
        /** 三档评价 */
        private Integer rating;
        private String content;
        /** 带饭方回应 */
        private String reply;
        /** 汇总审核态：0审核中 1全部通过 2含屏蔽图 */
        private Integer auditStatus;
        private LocalDateTime createTime;
        private List<GalleryImage> images;
    }

    /** 相册图片：卡片用 thumbUrl，点开大图才取 url */
    @Data
    public static class GalleryImage {
        private String url;
        private String thumbUrl;
    }
}
