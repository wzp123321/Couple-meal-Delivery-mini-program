package com.couplemeal.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 菜单与忌口库。
 *
 * <p>本接口只固化「跨模块调用」的方法签名（供 20-约定 发起时做忌口提醒用），
 * 模块自身的增删改查方法由实现方在此接口上增量添加。
 */
public interface MenuService {

    /**
     * 按菜名批量查忌口等级，供发起约定时生成 warnings。
     *
     * @param coupleId 关系隔离键
     * @param names    菜名（快照名），大小写与空格已由调用方 trim
     * @return {@code 菜名 -> avoidLevel}（0无 1少吃 2不吃 3过敏）；菜单库里查不到的菜名不出现在 map 中
     */
    Map<String, Integer> findAvoidLevels(Long coupleId, Collection<String> names);

    // ===== 以下为菜单模块自身的增删改查（均为增量追加，不改已有签名） =====

    /**
     * 菜单库列表。coupleId 只从 UserContext 取，所有查询固定带 status=1（软删隔离）。
     *
     * @param category     分类筛选 1荤 2素 3汤 4主食，传 null 不过滤
     * @param onlyFavorite true 只看「常点★」
     * @param creatorId    只看某人加的菜，传 null 不过滤
     * @param sort         {@code name}=菜名升序（默认，忌口置顶）；{@code recent}=最近点过倒序
     */
    List<MenuVO> list(Integer category, Boolean onlyFavorite, Long creatorId, String sort);

    /** 新增菜品，返回新 id。category / avoidLevel 非法 → 2004，同关系重名 → 2002 */
    Long create(String name, Integer category, Boolean favoriteFlag, Integer avoidLevel, String note);

    /** 全量修改菜品。菜品不存在/不属于本关系 → 2001 */
    void update(Long id, String name, Integer category, Boolean favoriteFlag, Integer avoidLevel, String note);

    /** 软删除（status=2）。不动 meal_plan_item，历史约定的菜名快照保持不变（D2） */
    void delete(Long id);

    /** 口味画像：总数、各分类数量、Top 菜、忌口清单 */
    ProfileResp profile();

    /** 智能推荐：排除 avoid_level ≥ 2，常点优先，其次「距离上次点单最久」 */
    List<MenuVO> recommend(Integer limit);

    // ===== 请求/响应对象（就近放置，不设 dto 包） =====

    /** 菜单列表 / 推荐列表的条目 */
    class MenuVO {
        public Long id;
        public String name;
        public Integer category;
        public String categoryName;
        /** 1 表示「常点★」 */
        public Integer favoriteFlag;
        public Integer avoidLevel;
        /** 「少吃 / 不吃 / 过敏」，0 级为 null */
        public String avoidLevelName;
        public Long creatorId;
        public String creatorNickname;
        public String note;
    }

    /** 口味画像 */
    class ProfileResp {
        public Integer totalCount;
        public List<CategoryCount> categoryCounts;
        public List<TopDish> topDishes;
        public List<AvoidVO> avoidDishes;
    }

    /** 分类分布（固定返回 4 类，便于前端直接渲染） */
    class CategoryCount {
        public Integer category;
        public String categoryName;
        public Integer count;
    }

    /** 最常点的菜（只统计 status=4 已完成的约定） */
    class TopDish {
        public String name;
        public Integer count;
    }

    /** 忌口清单（avoid_level > 0，只提醒不硬拦） */
    class AvoidVO {
        public Long id;
        public String name;
        public Integer avoidLevel;
        public String avoidLevelName;
    }
}
