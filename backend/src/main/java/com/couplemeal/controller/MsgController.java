package com.couplemeal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.couplemeal.common.PageResult;
import com.couplemeal.common.R;
import com.couplemeal.common.UserContext;
import com.couplemeal.entity.Notification;
import com.couplemeal.service.impl.NotifyServiceImpl;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息中心控制器（见 50-通知/后端.md §2）。
 *
 * <p>路径（context-path=/api）：{@code GET /api/msg/list}、{@code GET /api/msg/unread}、{@code POST /api/msg/read}。
 * 不接收 coupleId / userId 入参，一律从 UserContext 取（数据隔离铁律）；无有效关系返回 1002。
 *
 * <p>依赖 {@link NotifyServiceImpl} 而非 {@link com.couplemeal.service.NotifyService}：
 * 接口只固化了跨模块写入 / 角标的三个方法，消息列表是本控制器自用，故在实现上增量提供 {@code list}。
 */
@RestController
@RequestMapping("/msg")
@RequiredArgsConstructor
public class MsgController {

    /** 每页默认条数与上限（防 pageSize 过大拖垮库） */
    private static final long DEFAULT_PAGE_NO = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 50L;

    private final NotifyServiceImpl notifyService;

    /**
     * 消息列表，按时间倒序分页。
     */
    @GetMapping("/list")
    public R<PageResult<MsgResp>> list(@RequestParam(value = "pageNo", required = false) Long pageNo,
                                      @RequestParam(value = "pageSize", required = false) Long pageSize) {
        Long coupleId = UserContext.requireCoupleId();
        long current = pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        IPage<Notification> page = notifyService.list(coupleId, UserContext.getUserId(), current, size);
        List<MsgResp> records = page.getRecords().stream().map(MsgController::toResp).toList();
        return R.ok(PageResult.of(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records));
    }

    /**
     * 未读条数（Tab 角标来源）。
     */
    @GetMapping("/unread")
    public R<UnreadResp> unread() {
        UnreadResp resp = new UnreadResp();
        // 未读是「人」维度的红点，与关系无关，按接口契约只传 receiverId
        resp.unread = notifyService.countUnread(UserContext.getUserId());
        return R.ok(resp);
    }

    /**
     * 全部已读（进列表即清，不做单条已读）。
     */
    @PostMapping("/read")
    public R<Void> readAll() {
        notifyService.markAllRead(UserContext.getUserId());
        return R.ok();
    }

    private static MsgResp toResp(Notification n) {
        MsgResp resp = new MsgResp();
        resp.id = n.getId();
        resp.planId = n.getPlanId();
        resp.type = n.getType();
        resp.title = n.getTitle();
        resp.content = n.getContent();
        resp.readFlag = n.getReadFlag();
        resp.createTime = n.getCreateTime();
        return resp;
    }

    /**
     * 消息列表返回体。
     */
    @Data
    public static class MsgResp {
        /** 通知 id */
        private Long id;
        /** 关联约定；每日提醒为 null */
        private Long planId;
        /** 1新约定 2被认领 3已送达 4已完成 5每日提醒 6已打卡 */
        private Integer type;
        private String title;
        private String content;
        /** 0未读 1已读 */
        private Integer readFlag;
        private LocalDateTime createTime;
    }

    /**
     * 未读条数返回体。
     */
    @Data
    public static class UnreadResp {
        private long unread;
    }
}
