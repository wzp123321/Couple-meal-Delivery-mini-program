package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.entity.MealPlanItem;
import com.couplemeal.entity.ShoppingList;
import com.couplemeal.mapper.MealPlanItemMapper;
import com.couplemeal.mapper.MealPlanMapper;
import com.couplemeal.mapper.ShoppingListMapper;
import com.couplemeal.service.ShoppingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采购清单实现（shopping_list）。
 *
 * <p>三条要点（80-后端.md §3）：
 *   1. 幂等靠唯一索引 + catch DuplicateKeyException，不先 count。
 *   2. 勾选 updates 必带 couple_id，否则会越权改到别人的清单。
 *   3. 已买条目保留展示，不删行。
 */
@Slf4j
@Service
public class ShoppingServiceImpl implements ShoppingService {

    /** 单条食材名长度上限（列 varchar(128)） */
    private static final int ITEM_NAME_MAX_LEN = 128;
    /** 单次生成条数上限（MVP 一道菜一条，约定本身最多 10 道菜） */
    private static final int MAX_ITEMS = 50;
    /** 由约定菜品推导时的条目后缀（MVP 简化：一菜一条，留给人工改） */
    private static final String DERIVED_SUFFIX = " 的食材";

    private final MealPlanMapper mealPlanMapper;
    private final MealPlanItemMapper mealPlanItemMapper;
    private final ShoppingListMapper shoppingListMapper;

    public ShoppingServiceImpl(MealPlanMapper mealPlanMapper, MealPlanItemMapper mealPlanItemMapper,
                               ShoppingListMapper shoppingListMapper) {
        this.mealPlanMapper = mealPlanMapper;
        this.mealPlanItemMapper = mealPlanItemMapper;
        this.shoppingListMapper = shoppingListMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ItemVO> generate(Long planId, List<String> items) {
        Long cid = UserContext.requireCoupleId();
        MealPlan plan = requireOwnedPlan(planId, cid);
        assertGeneratable(plan);

        List<String> names = resolveItemNames(planId, items);
        int seq = nextSeq(planId);
        for (String name : names) {
            ShoppingList row = new ShoppingList();
            row.setId(IdUtil.nextId());
            row.setPlanId(planId);
            row.setCoupleId(cid);
            row.setItemName(name);
            row.setCheckedFlag(0);
            row.setSeq(seq);
            try {
                shoppingListMapper.insert(row);
                seq++;
            } catch (DuplicateKeyException e) {
                // 已存在，跳过；seq 不递增，避免重复生成后序号出现空洞
                log.debug("采购清单条目已存在，忽略 planId={} item={}", planId, name);
            }
        }
        return queryItems(planId, cid);
    }

    @Override
    public List<ItemVO> list(Long planId) {
        Long cid = UserContext.requireCoupleId();
        Long targetPlanId = planId;
        if (targetPlanId == null) {
            targetPlanId = findLatestUncheckedPlanId(cid);
            if (targetPlanId == null) {
                return List.of();
            }
        } else {
            requireOwnedPlan(targetPlanId, cid);
        }
        return queryItems(targetPlanId, cid);
    }

    @Override
    public void check(Long id, Integer checkedFlag) {
        Long cid = UserContext.requireCoupleId();
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 不能为空");
        }
        if (checkedFlag == null || (checkedFlag != 0 && checkedFlag != 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "checkedFlag 只能为 0 或 1");
        }
        LambdaUpdateWrapper<ShoppingList> uw = new LambdaUpdateWrapper<ShoppingList>()
                .eq(ShoppingList::getId, id)
                // 必带 couple_id：只按 id 更新会越权改到别人的清单（80-后端.md §6）
                .eq(ShoppingList::getCoupleId, cid)
                .set(ShoppingList::getCheckedFlag, checkedFlag)
                // 取消勾选时时间必须置回 null，否则排序按旧时间点错乱
                .set(ShoppingList::getCheckedTime, checkedFlag == 1 ? LocalDateTime.now() : null);
        if (shoppingListMapper.update(null, uw) == 0) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
    }

    // ===================== 私有：条目来源 =====================

    /**
     * 条目来源：显式传 items 则用它（去重去空）；否则由 meal_plan_item 的菜名推导，一菜一条。
     */
    private List<String> resolveItemNames(Long planId, List<String> items) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (items != null) {
            for (String raw : items) {
                if (raw == null || raw.trim().isEmpty()) {
                    continue;
                }
                String name = raw.trim();
                if (name.length() > ITEM_NAME_MAX_LEN) {
                    name = name.substring(0, ITEM_NAME_MAX_LEN);
                }
                if (seen.add(name)) {
                    names.add(name);
                }
            }
        }
        if (!names.isEmpty()) {
            return names;
        }
        List<MealPlanItem> planItems = mealPlanItemMapper.selectList(new LambdaQueryWrapper<MealPlanItem>()
                .eq(MealPlanItem::getPlanId, planId)
                .orderByAsc(MealPlanItem::getSeq)
                .orderByAsc(MealPlanItem::getId));
        for (MealPlanItem pi : planItems) {
            if (names.size() >= MAX_ITEMS) {
                break;
            }
            String name = pi.getName() == null ? null : pi.getName().trim();
            if (name != null && !name.isEmpty() && seen.add(name)) {
                names.add(clipLen(name + DERIVED_SUFFIX));
            }
        }
        return names;
    }

    private String clipLen(String name) {
        return name.length() > ITEM_NAME_MAX_LEN ? name.substring(0, ITEM_NAME_MAX_LEN) : name;
    }

    /** 新条目接在当前最大 seq 之后，重复生成不会互相顶掉 */
    private int nextSeq(Long planId) {
        ShoppingList last = shoppingListMapper.selectOne(new LambdaQueryWrapper<ShoppingList>()
                .eq(ShoppingList::getPlanId, planId)
                .orderByDesc(ShoppingList::getSeq)
                .last("LIMIT 1"));
        return last == null || last.getSeq() == null ? 1 : last.getSeq() + 1;
    }

    // ===================== 私有：查询 =====================

    /** 未买的在前，其次按 seq、id 升序；已买条目保留返回（前端置底灰色） */
    private List<ItemVO> queryItems(Long planId, Long coupleId) {
        List<ShoppingList> rows = shoppingListMapper.selectList(new LambdaQueryWrapper<ShoppingList>()
                .eq(ShoppingList::getPlanId, planId)
                .eq(ShoppingList::getCoupleId, coupleId)
                .orderByAsc(ShoppingList::getCheckedFlag)
                .orderByAsc(ShoppingList::getSeq)
                .orderByAsc(ShoppingList::getId));
        List<ItemVO> vos = new ArrayList<>(rows.size());
        for (ShoppingList row : rows) {
            vos.add(toVO(row));
        }
        return vos;
    }

    /** 「最近一条有未买条目」的约定：按该约定未买条目的最晚创建时间倒序取第一条 */
    private Long findLatestUncheckedPlanId(Long coupleId) {
        List<Map<String, Object>> rows = shoppingListMapper.selectMaps(new QueryWrapper<ShoppingList>()
                .select("plan_id", "MAX(create_time) AS last_time")
                .eq("couple_id", coupleId)
                .eq("checked_flag", 0)
                .groupBy("plan_id")
                .orderByDesc("last_time")
                .last("LIMIT 1"));
        if (rows.isEmpty() || rows.get(0).get("plan_id") == null) {
            return null;
        }
        return ((Number) rows.get(0).get("plan_id")).longValue();
    }

    private ItemVO toVO(ShoppingList row) {
        ItemVO vo = new ItemVO();
        vo.id = row.getId();
        vo.planId = row.getPlanId();
        vo.itemName = row.getItemName();
        vo.checkedFlag = row.getCheckedFlag();
        vo.checkedTime = row.getCheckedTime();
        vo.seq = row.getSeq();
        return vo;
    }

    // ===================== 私有：校验 =====================

    /** 约定必须存在且属于当前关系，否则 1002（不泄露"这条数据存在但不属于你"，见 00-全局约定.md §3.4） */
    private MealPlan requireOwnedPlan(Long planId, Long coupleId) {
        if (planId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "planId 不能为空");
        }
        MealPlan plan = mealPlanMapper.selectById(planId);
        if (plan == null || !coupleId.equals(plan.getCoupleId())) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        return plan;
    }

    /** 终态约定（4已完成 / 5已取消 / 6已过期）不再生成清单 */
    private void assertGeneratable(MealPlan plan) {
        Integer status = plan.getStatus();
        if (status == null || status > PlanStatus.DELIVERED.getCode()) {
            throw new BizException(ErrorCode.STATUS_CONFLICT);
        }
    }
}
