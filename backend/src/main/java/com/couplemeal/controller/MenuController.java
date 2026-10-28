package com.couplemeal.controller;

import com.couplemeal.common.R;
import com.couplemeal.service.MenuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单控制器：菜单库列表 / 新增 / 修改 / 删除 / 口味画像 / 智能推荐。
 *
 * <p>路径（context-path=/api）：/api/menu/...，全部走 AuthInterceptor 鉴权。
 * 所有接口不接收 coupleId 入参（数据隔离铁律），coupleId 只从 UserContext 取。
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /**
     * 菜单库列表。
     *
     * @param category     1荤 2素 3汤 4主食，不传不过滤
     * @param onlyFavorite true 只看「常点★」（UI 文案「常点」= favorite_flag）
     * @param creatorId    只看某人加的菜
     * @param sort         name（默认，忌口置顶）/ recent（最近点过）
     */
    @GetMapping("/list")
    public R<List<MenuService.MenuVO>> list(@RequestParam(required = false) Integer category,
                                            @RequestParam(required = false) Boolean onlyFavorite,
                                            @RequestParam(required = false) Long creatorId,
                                            @RequestParam(required = false, defaultValue = "name") String sort) {
        return R.ok(menuService.list(category, onlyFavorite, creatorId, sort));
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody MenuReq req) {
        return R.ok(menuService.create(req.getName(), req.getCategory(), req.getFavoriteFlag(),
                req.getAvoidLevel(), req.getNote()));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MenuReq req) {
        menuService.update(id, req.getName(), req.getCategory(), req.getFavoriteFlag(),
                req.getAvoidLevel(), req.getNote());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }

    @GetMapping("/profile")
    public R<MenuService.ProfileResp> profile() {
        return R.ok(menuService.profile());
    }

    @GetMapping("/recommend")
    public R<List<MenuService.MenuVO>> recommend(@RequestParam(required = false) Integer limit) {
        return R.ok(menuService.recommend(limit));
    }

    /** 新增 / 修改共用请求体（全量覆盖） */
    @Data
    public static class MenuReq {
        @NotBlank(message = "菜名不能为空")
        private String name;

        /** 1荤 2素 3汤 4主食 */
        @NotNull(message = "请选择分类")
        private Integer category;

        /** 常点★，不传视为 false */
        private Boolean favoriteFlag;

        /** 0无 1少吃 2不吃 3过敏，不传视为 0 */
        private Integer avoidLevel;

        /** 菜谱笔记 */
        private String note;
    }
}
