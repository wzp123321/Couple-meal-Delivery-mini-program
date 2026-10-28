package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.domain.enums.AvoidLevel;
import com.couplemeal.domain.enums.MenuCategory;
import com.couplemeal.domain.enums.MenuStatus;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.MealPlanItem;
import com.couplemeal.entity.MenuItem;
import com.couplemeal.entity.User;
import com.couplemeal.mapper.MealPlanItemMapper;
import com.couplemeal.mapper.MenuItemMapper;
import com.couplemeal.mapper.UserMapper;
import com.couplemeal.service.MenuService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 菜单与偏好实现（menu_item + meal_plan_item 只读统计）。
 *
 * <p>三条铁律：
 *   1. 所有业务查询固定带 {@code status = 1}，软删的菜（status=2）永不再出现。
 *   2. 删除只改 menu_item.status，不动 meal_plan_item —— 那里是菜名快照（D2）。
 *   3. 忌口「只提醒不硬拦」：本实现类不做任何拦截，只对外提供 {@link #findAvoidLevels}。
 *
 * <p>Mapper 只开放 BaseMapper 能力，复杂排序/聚合一律用 QueryWrapper 拼，不新增 @Select（mapper 包不归本模块改）。
 */
@Service
public class MenuServiceImpl implements MenuService {

    /** 菜名长度上限，超限 2004 */
    private static final int NAME_MAX_LEN = 20;
    /** 菜谱笔记长度上限（列 varchar(512)） */
    private static final int NOTE_MAX_LEN = 512;
    /** 口味画像「最常点的菜」条数 */
    private static final int TOP_DISH_LIMIT = 5;
    /** 推荐默认条数 / 单次上限 */
    private static final int RECOMMEND_DEFAULT_LIMIT = 5;
    private static final int RECOMMEND_MAX_LIMIT = 20;

    /**
     * 某道菜「最近被点过」的日期（标量子查询）：反查 meal_plan_item.menu_item_id → meal_plan.plan_date。
     * 只排除 5已取消 / 6已过期，进行中的也算点过（当天想再点一次）。
     * 注意：BaseMapper 生成的 SQL 不给表起别名，相关列必须写成 {@code menu_item.xxx}。
     */
    private static final String LAST_ORDERED_SQL =
            "(SELECT MAX(p.plan_date) FROM meal_plan_item pi " +
                    "JOIN meal_plan p ON p.id = pi.plan_id " +
                    "AND p.couple_id = menu_item.couple_id " +
                    "AND p.status NOT IN (" + PlanStatus.CANCELLED.getCode() + ", " + PlanStatus.EXPIRED.getCode() + ") " +
                    "WHERE pi.menu_item_id = menu_item.id)";
    /** 从没点过的菜没有日期，兜底成一个极早的日期，好让它稳定排在「最久没吃」的位置 */
    private static final String LAST_ORDERED_COALESCE_SQL = "COALESCE(" + LAST_ORDERED_SQL + ", '1970-01-01')";

    private final MenuItemMapper menuItemMapper;
    private final MealPlanItemMapper mealPlanItemMapper;
    private final UserMapper userMapper;

    public MenuServiceImpl(MenuItemMapper menuItemMapper, MealPlanItemMapper mealPlanItemMapper, UserMapper userMapper) {
        this.menuItemMapper = menuItemMapper;
        this.mealPlanItemMapper = mealPlanItemMapper;
        this.userMapper = userMapper;
    }

    // ===================== 忌口提醒（供 20-约定 调用） =====================

    @Override
    public Map<String, Integer> findAvoidLevels(Long coupleId, Collection<String> names) {
        if (coupleId == null || names == null || names.isEmpty()) {
            return Map.of();
        }
        List<String> distinct = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : names) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (!name.isEmpty() && seen.add(name)) {
                distinct.add(name);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<MenuItem> qw = new LambdaQueryWrapper<MenuItem>()
                .eq(MenuItem::getCoupleId, coupleId)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                .in(MenuItem::getName, distinct)
                .select(MenuItem::getName, MenuItem::getAvoidLevel);
        Map<String, Integer> levels = new HashMap<>();
        for (MenuItem item : menuItemMapper.selectList(qw)) {
            levels.put(item.getName(), item.getAvoidLevel());
        }
        return levels;
    }

    // ===================== 列表 / 新增 / 修改 / 删除 =====================

    @Override
    public List<MenuVO> list(Integer category, Boolean onlyFavorite, Long creatorId, String sort) {
        Long cid = UserContext.requireCoupleId();
        if (category != null && categoryOf(category) == null) {
            throw new BizException(ErrorCode.MENU_NAME_TOO_LONG);
        }
        LambdaQueryWrapper<MenuItem> qw = new LambdaQueryWrapper<MenuItem>()
                .eq(MenuItem::getCoupleId, cid)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                .eq(category != null, MenuItem::getCategory, category)
                .eq(Boolean.TRUE.equals(onlyFavorite), MenuItem::getFavoriteFlag, 1)
                .eq(creatorId != null, MenuItem::getCreatorId, creatorId);
        if ("recent".equalsIgnoreCase(sort)) {
            // 最近点过：点过的按日期倒序在前，从没点过的排最后（IS NULL 升序）
            qw.last("ORDER BY " + LAST_ORDERED_SQL + " IS NULL ASC, " + LAST_ORDERED_SQL
                    + " DESC, avoid_level DESC, create_time DESC");
        } else {
            // sort=name（默认）：忌口置顶（3过敏 > 2不吃 > 1少吃），其次菜名升序
            qw.orderByDesc(MenuItem::getAvoidLevel).orderByAsc(MenuItem::getName);
        }
        return toVOs(menuItemMapper.selectList(qw));
    }

    @Override
    public Long create(String name, Integer category, Boolean favoriteFlag, Integer avoidLevel, String note) {
        Long cid = UserContext.requireCoupleId();
        String safeName = normalizeName(name);
        checkCategory(category, avoidLevel);
        checkDuplicateName(cid, safeName, null);

        MenuItem item = new MenuItem();
        item.setId(IdUtil.nextId());
        item.setCoupleId(cid);
        item.setName(safeName);
        item.setCategory(category);
        item.setFavoriteFlag(Boolean.TRUE.equals(favoriteFlag) ? 1 : 0);
        item.setAvoidLevel(avoidLevel == null ? AvoidLevel.NONE.getCode() : avoidLevel);
        item.setCreatorId(UserContext.getUserId());
        item.setNote(normalizeNote(note));
        item.setStatus(MenuStatus.NORMAL.getCode());
        menuItemMapper.insert(item);
        return item.getId();
    }

    @Override
    public void update(Long id, String name, Integer category, Boolean favoriteFlag, Integer avoidLevel, String note) {
        Long cid = UserContext.requireCoupleId();
        String safeName = normalizeName(name);
        checkCategory(category, avoidLevel);
        requireOwned(id, cid);
        checkDuplicateName(cid, safeName, id);

        LambdaUpdateWrapper<MenuItem> uw = new LambdaUpdateWrapper<MenuItem>()
                .eq(MenuItem::getId, id)
                .eq(MenuItem::getCoupleId, cid)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                .set(MenuItem::getName, safeName)
                .set(MenuItem::getCategory, category)
                .set(MenuItem::getFavoriteFlag, Boolean.TRUE.equals(favoriteFlag) ? 1 : 0)
                .set(MenuItem::getAvoidLevel, avoidLevel == null ? AvoidLevel.NONE.getCode() : avoidLevel)
                .set(MenuItem::getNote, normalizeNote(note));
        menuItemMapper.update(null, uw);
    }

    @Override
    public void delete(Long id) {
        Long cid = UserContext.requireCoupleId();
        requireOwned(id, cid);
        // 软删：只改状态，不删行；历史约定的菜名快照（meal_plan_item.name）原样保留
        LambdaUpdateWrapper<MenuItem> uw = new LambdaUpdateWrapper<MenuItem>()
                .eq(MenuItem::getId, id)
                .eq(MenuItem::getCoupleId, cid)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                .set(MenuItem::getStatus, MenuStatus.DELETED.getCode());
        if (menuItemMapper.update(null, uw) == 0) {
            throw new BizException(ErrorCode.MENU_ITEM_NOT_FOUND);
        }
    }

    // ===================== 口味画像 =====================

    @Override
    public ProfileResp profile() {
        Long cid = UserContext.requireCoupleId();
        ProfileResp resp = new ProfileResp();
        resp.totalCount = countMenu(cid, null);
        resp.categoryCounts = new ArrayList<>(MenuCategory.values().length);
        for (MenuCategory mc : MenuCategory.values()) {
            CategoryCount cc = new CategoryCount();
            cc.category = mc.getCode();
            cc.categoryName = mc.getDesc();
            cc.count = countMenu(cid, mc.getCode());
            resp.categoryCounts.add(cc);
        }
        resp.topDishes = listTopDishes(cid);
        // 忌口清单：avoid_level > 0，级别高的（过敏）排在前面，前端不再重排
        List<MenuItem> avoidItems = menuItemMapper.selectList(new LambdaQueryWrapper<MenuItem>()
                .eq(MenuItem::getCoupleId, cid)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                .gt(MenuItem::getAvoidLevel, AvoidLevel.NONE.getCode())
                .orderByDesc(MenuItem::getAvoidLevel)
                .orderByAsc(MenuItem::getName)
                .select(MenuItem::getId, MenuItem::getName, MenuItem::getAvoidLevel));
        resp.avoidDishes = new ArrayList<>(avoidItems.size());
        for (MenuItem item : avoidItems) {
            resp.avoidDishes.add(toAvoidVO(item));
        }
        return resp;
    }

    /**
     * 最常点的菜：聚合 meal_plan_item.<strong>name</strong>（快照名），
     * 不 join menu_item —— 菜单改名/删除不会污染历史统计（30-后端.md §3.4）。
     * 只统计 status=4 已完成：取消 / 过期的约定不算「吃过」。
     */
    private List<TopDish> listTopDishes(Long coupleId) {
        QueryWrapper<MealPlanItem> qw = new QueryWrapper<MealPlanItem>()
                .select("`name`", "COUNT(*) AS cnt")
                .apply("plan_id IN (SELECT id FROM meal_plan WHERE couple_id = {0} AND status = {1})",
                        coupleId, PlanStatus.COMPLETED.getCode())
                .groupBy("`name`")
                .orderByDesc("cnt")
                .last("LIMIT " + TOP_DISH_LIMIT);
        List<Map<String, Object>> rows = mealPlanItemMapper.selectMaps(qw);
        List<TopDish> dishes = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            TopDish dish = new TopDish();
            Object name = row.get("name");
            Object cnt = row.get("cnt");
            dish.name = name == null ? null : String.valueOf(name);
            dish.count = cnt instanceof Number n ? n.intValue() : 0;
            dishes.add(dish);
        }
        return dishes;
    }

    // ===================== 智能推荐 =====================

    @Override
    public List<MenuVO> recommend(Integer limit) {
        Long cid = UserContext.requireCoupleId();
        int size;
        if (limit == null || limit <= 0) {
            size = RECOMMEND_DEFAULT_LIMIT;
        } else {
            size = Math.min(limit, RECOMMEND_MAX_LIMIT);
        }
        LambdaQueryWrapper<MenuItem> qw = new LambdaQueryWrapper<MenuItem>()
                .eq(MenuItem::getCoupleId, cid)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                // 排除「不吃 / 过敏」：推荐里绝不出现（30-后端.md §3.5 规则 1）
                .lt(MenuItem::getAvoidLevel, AvoidLevel.NO.getCode());
        // 常点优先（规则 2）→ 距离上次点单最久的菜，从没点过视为最久（规则 3）→ 同分类其余菜自然补位（规则 4）
        qw.last("ORDER BY favorite_flag DESC, " + LAST_ORDERED_COALESCE_SQL
                + " ASC, create_time DESC LIMIT " + size);
        return toVOs(menuItemMapper.selectList(qw));
    }

    // ===================== 私有：校验 =====================

    private String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "菜名不能为空");
        }
        if (name.length() > NAME_MAX_LEN) {
            throw new BizException(ErrorCode.MENU_NAME_TOO_LONG);
        }
        return name;
    }

    private String normalizeNote(String raw) {
        if (raw == null) {
            return null;
        }
        String note = raw.trim();
        if (note.length() > NOTE_MAX_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "菜谱笔记不能超过 " + NOTE_MAX_LEN + " 个字");
        }
        return note.isEmpty() ? null : note;
    }

    /** 新增 / 修改：分类必填且必须在枚举范围内，忌口可不传；非法值按 30-后端.md §3.1 返回 2004 */
    private void checkCategory(Integer category, Integer avoidLevel) {
        if (category == null || categoryOf(category) == null) {
            throw new BizException(ErrorCode.MENU_NAME_TOO_LONG);
        }
        if (avoidLevel != null && levelOf(avoidLevel) == null) {
            throw new BizException(ErrorCode.MENU_NAME_TOO_LONG);
        }
    }

    /** 同关系内重名校验（30-后端.md §3.1）。excludeId 非空时排除自己，否则「编辑不改名」必报 2002 */
    private void checkDuplicateName(Long coupleId, String name, Long excludeId) {
        LambdaQueryWrapper<MenuItem> qw = new LambdaQueryWrapper<MenuItem>()
                .eq(MenuItem::getCoupleId, coupleId)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                .eq(MenuItem::getName, name)
                .ne(excludeId != null, MenuItem::getId, excludeId);
        if (menuItemMapper.selectCount(qw) > 0) {
            throw new BizException(ErrorCode.MENU_NAME_DUPLICATE);
        }
    }

    /** 按 id 取本关系的正常菜品；不存在 / 不属于本关系 → 2001（与"引用已删菜品"同码） */
    private MenuItem requireOwned(Long id, Long coupleId) {
        if (id == null) {
            throw new BizException(ErrorCode.MENU_ITEM_NOT_FOUND);
        }
        MenuItem item = menuItemMapper.selectById(id);
        if (item == null
                || !coupleId.equals(item.getCoupleId())
                || !Objects.equals(item.getStatus(), MenuStatus.NORMAL.getCode())) {
            throw new BizException(ErrorCode.MENU_ITEM_NOT_FOUND);
        }
        return item;
    }

    // ===================== 私有：统计与转换 =====================

    private int countMenu(Long coupleId, Integer category) {
        LambdaQueryWrapper<MenuItem> qw = new LambdaQueryWrapper<MenuItem>()
                .eq(MenuItem::getCoupleId, coupleId)
                .eq(MenuItem::getStatus, MenuStatus.NORMAL.getCode())
                .eq(category != null, MenuItem::getCategory, category);
        return menuItemMapper.selectCount(qw).intValue();
    }

    private List<MenuVO> toVOs(List<MenuItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Set<Long> creatorIds = new HashSet<>();
        for (MenuItem item : items) {
            if (item.getCreatorId() != null) {
                creatorIds.add(item.getCreatorId());
            }
        }
        Map<Long, String> nicknames = new HashMap<>();
        if (!creatorIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(creatorIds)) {
                nicknames.put(u.getId(), u.getNickname());
            }
        }
        List<MenuVO> vos = new ArrayList<>(items.size());
        for (MenuItem item : items) {
            MenuVO vo = new MenuVO();
            vo.id = item.getId();
            vo.name = item.getName();
            vo.category = item.getCategory();
            vo.categoryName = categoryName(item.getCategory());
            vo.favoriteFlag = item.getFavoriteFlag();
            vo.avoidLevel = item.getAvoidLevel();
            vo.avoidLevelName = levelName(item.getAvoidLevel());
            vo.creatorId = item.getCreatorId();
            vo.creatorNickname = nicknames.get(item.getCreatorId());
            vo.note = item.getNote();
            vos.add(vo);
        }
        return vos;
    }

    private AvoidVO toAvoidVO(MenuItem item) {
        AvoidVO vo = new AvoidVO();
        vo.id = item.getId();
        vo.name = item.getName();
        vo.avoidLevel = item.getAvoidLevel();
        vo.avoidLevelName = levelName(item.getAvoidLevel());
        return vo;
    }

    /** 忌口 0 级不展示文案（前端按 avoidLevel > 0 决定是否染色） */
    private String levelName(Integer avoidLevel) {
        AvoidLevel level = avoidLevel == null ? null : levelOf(avoidLevel);
        return (level == null || level == AvoidLevel.NONE) ? null : level.getDesc();
    }

    /** 分类文案；脏数据（非法 code）返回 null，不让整个列表查询炸掉 */
    private String categoryName(Integer category) {
        MenuCategory mc = category == null ? null : categoryOf(category);
        return mc == null ? null : mc.getDesc();
    }

    private MenuCategory categoryOf(int code) {
        for (MenuCategory c : MenuCategory.values()) {
            if (c.getCode() == code) {
                return c;
            }
        }
        return null;
    }

    private AvoidLevel levelOf(int code) {
        for (AvoidLevel a : AvoidLevel.values()) {
            if (a.getCode() == code) {
                return a;
            }
        }
        return null;
    }
}
