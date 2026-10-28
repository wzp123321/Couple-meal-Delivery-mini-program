package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.common.UserContext;
import com.couplemeal.service.LoveValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 爱心值控制器（见 40-爱心值/后端.md §2）。
 *
 * <p>路径（context-path=/api）：{@code GET /api/love/summary}。
 * 无 coupleId / userId 入参，全部从 UserContext 取（数据隔离铁律）；无有效关系时由
 * {@link UserContext#requireCoupleId()} 抛 BizException(1002)，经 GlobalExceptionHandler 转 R。
 *
 * <p>本模块只提供读取：写入入口是 {@link LoveValueService#award}，由 20-约定的 complete / autoComplete 调用。
 */
@RestController
@RequestMapping("/love")
@RequiredArgsConstructor
public class LoveController {

    private final LoveValueService loveValueService;

    /**
     * 爱心值汇总：双方合计 / 我的 / 对方的 + 最近 10 条流水。
     */
    @GetMapping("/summary")
    public R<LoveValueService.Summary> summary() {
        Long coupleId = UserContext.requireCoupleId();
        return R.ok(loveValueService.summary(coupleId, UserContext.getUserId()));
    }
}
