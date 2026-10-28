package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.domain.ReportVO;
import com.couplemeal.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 月报 / 年报（模块 70 §3.4）：{@code GET /api/report}。
 *
 * <p>{@code ?scope=month&anchor=2026-09} / {@code ?scope=year&anchor=2026}；
 * anchor 缺省时月报取上一自然月、年报取当年。
 * 统计口径只有「已完成」（status=4），与爱心值加分口径一致；空期间返回 planCount=0 的空报告。
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final HistoryService historyService;

    @GetMapping
    public R<ReportVO> report(
            @RequestParam(name = "scope", defaultValue = "month") String scope,
            @RequestParam(name = "anchor", required = false) String anchor) {
        return R.ok(historyService.report(scope, anchor));
    }
}
