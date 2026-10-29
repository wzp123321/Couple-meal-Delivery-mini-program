package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.RpsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 石头剪刀布协商接口（phase-2）。
 *
 * <p>路径前缀 /api/rps；写入接口自动经 AuthInterceptor 注入当前用户与关系，入参不接收 coupleId。
 */
@RestController
@RequestMapping("/rps")
@RequiredArgsConstructor
public class RpsController {

    private final RpsService rpsService;

    @PostMapping("/create")
    public R<RpsService.RpsGameVO> create(@Valid @RequestBody CreateReq req) {
        return R.ok(rpsService.create(req.getMealType(), req.getPlanDate()));
    }

    @PostMapping("/{gameId}/throw")
    public R<RpsService.RpsGameVO> throwChoice(@PathVariable("gameId") Long gameId,
                                               @Valid @RequestBody ThrowReq req) {
        return R.ok(rpsService.throwChoice(gameId, req.getChoice()));
    }

    @PostMapping("/{gameId}/adopt")
    public R<AdoptResult> adopt(@PathVariable("gameId") Long gameId,
                                @Valid @RequestBody AdoptReq req) {
        Long planId = rpsService.adopt(gameId, req.getItems(), req.getRemark());
        AdoptResult r = new AdoptResult();
        r.planId = planId;
        return R.ok(r);
    }

    @GetMapping("/{gameId}")
    public R<RpsService.RpsGameVO> get(@PathVariable("gameId") Long gameId) {
        return R.ok(rpsService.get(gameId));
    }

    @GetMapping("/current")
    public R<RpsService.RpsGameVO> current(@RequestParam("mealType") int mealType,
                                           @RequestParam("planDate") String planDate) {
        return R.ok(rpsService.current(mealType, LocalDate.parse(planDate)));
    }

    // ===== 入参（静态内部类） =====

    public static class CreateReq {
        @NotNull(message = "餐次不能为空")
        private Integer mealType;
        @NotNull(message = "日期不能为空")
        private LocalDate planDate;

        public Integer getMealType() { return mealType; }
        public void setMealType(Integer mealType) { this.mealType = mealType; }
        public LocalDate getPlanDate() { return planDate; }
        public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    }

    public static class ThrowReq {
        @NotNull(message = "请出拳")
        private Integer choice;

        public Integer getChoice() { return choice; }
        public void setChoice(Integer choice) { this.choice = choice; }
    }

    public static class AdoptReq {
        private List<RpsService.ItemInput> items;
        private String remark;

        public List<RpsService.ItemInput> getItems() { return items; }
        public void setItems(List<RpsService.ItemInput> items) { this.items = items; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class AdoptResult {
        public Long planId;
    }
}
