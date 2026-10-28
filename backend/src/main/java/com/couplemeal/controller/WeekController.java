package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.domain.WeekVO;
import com.couplemeal.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 周历（模块 90）：{@code GET /api/plan/week}。
 *
 * <p>MVP 只做只读周历视图，「协商谁带」是二期，不实现。
 * 固定返回 7 天（周一至周日），没有约定的日子也占位，前端才能画满 7 行。
 */
@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class WeekController {

    private final HistoryService historyService;

    /**
     * 周历。
     *
     * @param startDate 周内任意日期（服务端对齐到该周周一）；缺省为本周一；超出 ±4 周返回空周
     */
    @GetMapping("/week")
    public R<WeekVO> week(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return R.ok(historyService.week(startDate));
    }
}
