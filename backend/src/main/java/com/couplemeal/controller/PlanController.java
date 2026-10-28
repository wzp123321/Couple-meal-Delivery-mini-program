package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.PlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 约定控制器（核心模块）。
 *
 * <p>路径（context-path=/api）：/api/plan/create、/api/plan/{id}、/api/plan/today、
 * /api/plan/{id}/{claim|withdraw|confirm|reject|deliver|complete|cancel|reassign}。
 * 全部走 AuthInterceptor 鉴权。
 *
 * <p>两条硬性约定：
 * <ol>
 *   <li><b>不接收 status 入参</b>（D8）：状态由具体动作经 PlanStateMachine 推导，前端传不进来；</li>
 *   <li><b>不接收 coupleId 入参</b>：关系隔离键只从 UserContext 取（数据隔离铁律）。</li>
 * </ol>
 * 请求/响应用静态内部类承载，不设 dto 包。
 */
@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /** 缺省餐次：MVP 只有午饭 */
    private static final int DEFAULT_MEAL_TYPE = 1;

    @PostMapping("/create")
    public R<PlanService.CreateResult> create(@Valid @RequestBody CreateReq req) {
        List<PlanService.ItemInput> inputs = req.getItems().stream().map(item -> {
            PlanService.ItemInput input = new PlanService.ItemInput();
            input.menuItemId = item.getMenuItemId();
            input.name = item.getName();
            return input;
        }).toList();
        int mealType = req.getMealType() == null ? DEFAULT_MEAL_TYPE : req.getMealType();
        return R.ok(planService.create(mealType, req.getSource(), req.getPlanDate(), inputs, req.getRemark()));
    }

    @GetMapping("/today")
    public R<List<PlanService.PlanBrief>> today() {
        return R.ok(planService.today());
    }

    @GetMapping("/{id}")
    public R<PlanService.PlanDetail> detail(@PathVariable Long id) {
        return R.ok(planService.detail(id));
    }

    @PostMapping("/{id}/claim")
    public R<Void> claim(@PathVariable Long id) {
        planService.claim(id);
        return R.ok();
    }

    @PostMapping("/{id}/withdraw")
    public R<Void> withdraw(@PathVariable Long id) {
        planService.withdraw(id);
        return R.ok();
    }

    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        planService.confirm(id);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        planService.reject(id, req == null ? null : req.getReason());
        return R.ok();
    }

    @PostMapping("/{id}/deliver")
    public R<Void> deliver(@PathVariable Long id, @Valid @RequestBody DeliverReq req) {
        planService.deliver(id, req.getDeliverLocation(), req.getDeliverNote());
        return R.ok();
    }

    @PostMapping("/{id}/complete")
    public R<CompleteResp> complete(@PathVariable Long id) {
        return R.ok(new CompleteResp(planService.complete(id)));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        planService.cancel(id, req == null ? null : req.getReason());
        return R.ok();
    }

    @PostMapping("/{id}/reassign")
    public R<Void> reassign(@PathVariable Long id, @RequestBody(required = false) ReassignReq req) {
        planService.reassign(id, req == null ? null : req.getCarrierId());
        return R.ok();
    }

    // ===================== 入参 =====================

    @Data
    public static class CreateReq {
        /** 1午饭 2晚饭，缺省午饭（MVP 仅午饭） */
        private Integer mealType = 1;
        /** 0点餐 1主动带 */
        @NotNull(message = "来源不能为空")
        private Integer source;
        /** 约定日期，必须是工作日且在 [今天, 今天+7] 内 */
        @NotNull(message = "约定日期不能为空")
        private LocalDate planDate;
        /** 菜品快照列表，1–10 道 */
        @NotEmpty(message = "请至少选择一道菜")
        private List<ItemReq> items;
        /** 口味/时间/忌口备注，≤100 字 */
        private String remark;
    }

    @Data
    public static class ItemReq {
        /** 来源菜品，手输菜名为空 */
        private Long menuItemId;
        /** 菜名快照，≤20 字 */
        @NotBlank(message = "菜名不能为空")
        private String name;
    }

    @Data
    public static class ReasonReq {
        /** 取消必填；婉拒可空，默认为「婉拒」 */
        private String reason;
    }

    @Data
    public static class DeliverReq {
        /** 交付位置：前台/工位/门口 */
        private String deliverLocation;
        /** 便签留言，≤80 字 */
        private String deliverNote;
    }

    @Data
    public static class ReassignReq {
        /** 改派对象，必须是关系内的另一方；为空表示直接退回待认领 */
        private Long carrierId;
    }

    // ===================== 出参 =====================

    @Data
    public static class CompleteResp {
        /** 本单发放后关系合计爱心值 */
        private Long loveValue;

        public CompleteResp() {
        }

        public CompleteResp(Long loveValue) {
            this.loveValue = loveValue;
        }
    }
}
