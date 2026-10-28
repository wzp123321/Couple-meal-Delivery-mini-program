package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.ShoppingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购清单控制器：生成（幂等）/ 查询 / 勾选。
 *
 * <p>路径（context-path=/api）：/api/shopping/...，全部走 AuthInterceptor 鉴权。
 * 不接收 coupleId 入参，归属校验在 Service 内完成。
 */
@RestController
@RequestMapping("/shopping")
@RequiredArgsConstructor
public class ShoppingController {

    private final ShoppingService shoppingService;

    @PostMapping("/generate")
    public R<List<ShoppingService.ItemVO>> generate(@Valid @RequestBody GenerateReq req) {
        return R.ok(shoppingService.generate(req.getPlanId(), req.getItems()));
    }

    /**
     * 采购清单查询。
     *
     * @param planId 不传时取「最近一条有未买条目」的清单
     */
    @GetMapping("/list")
    public R<List<ShoppingService.ItemVO>> list(@RequestParam(required = false) Long planId) {
        return R.ok(shoppingService.list(planId));
    }

    @PostMapping("/check")
    public R<Void> check(@Valid @RequestBody CheckReq req) {
        shoppingService.check(req.getId(), req.getCheckedFlag());
        return R.ok();
    }

    @Data
    public static class GenerateReq {
        @NotNull(message = "planId 不能为空")
        private Long planId;

        /** 显式食材名；不传则由约定菜品推导 */
        private List<String> items;
    }

    @Data
    public static class CheckReq {
        @NotNull(message = "id 不能为空")
        private Long id;

        /** 0 未买 / 1 已买 */
        @NotNull(message = "checkedFlag 不能为空")
        private Integer checkedFlag;
    }
}
