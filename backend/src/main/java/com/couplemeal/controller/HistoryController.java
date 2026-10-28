package com.couplemeal.controller;

import com.couplemeal.common.PageResult;
import com.couplemeal.common.R;
import com.couplemeal.domain.HistoryItem;
import com.couplemeal.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 历史列表（模块 70）：{@code GET /api/plan/history}。
 *
 * <p>路径说明：与 {@code PlanController} 共用 {@code /plan} 前缀但完整路径不冲突
 * （本类只挂 {@code /plan/history}，约定模块的 {@code /plan/create}、{@code /plan/today}、
 * {@code /plan/{id}} 由 PlanController 承载）。
 *
 * <p>只读视图：不接收 coupleId / status 等越权字段，coupleId 只从 UserContext 取。
 */
@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 历史列表（含已完成 / 已取消 / 已过期，今天的约定不进历史）。
     *
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20、上限 50
     * @param status   状态多选（0–6），如 {@code ?status=4&status=5}
     * @param role     角色筛选：{@code orderer} 我点的 / {@code carrier} 我带的
     */
    @GetMapping("/history")
    public R<PageResult<HistoryItem>> history(
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(name = "status", required = false) List<Integer> status,
            @RequestParam(name = "role", required = false) String role) {
        return R.ok(historyService.history(pageNo, pageSize, status, role));
    }
}
