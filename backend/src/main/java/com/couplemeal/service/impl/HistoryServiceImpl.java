package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.PageResult;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.WorkdayUtil;
import com.couplemeal.domain.HistoryItem;
import com.couplemeal.domain.ReportVO;
import com.couplemeal.domain.WeekVO;
import com.couplemeal.domain.enums.AuditStatus;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.Couple;
import com.couplemeal.entity.LoveValue;
import com.couplemeal.entity.MealCheckin;
import com.couplemeal.entity.MealCheckinImage;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.entity.MealPlanItem;
import com.couplemeal.entity.User;
import com.couplemeal.mapper.CoupleMapper;
import com.couplemeal.mapper.LoveValueMapper;
import com.couplemeal.mapper.MealCheckinImageMapper;
import com.couplemeal.mapper.MealCheckinMapper;
import com.couplemeal.mapper.MealPlanItemMapper;
import com.couplemeal.mapper.MealPlanMapper;
import com.couplemeal.mapper.UserMapper;
import com.couplemeal.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 历史 / 周历 / 报告实现（模块 70 + 90）。
 *
 * <p>全部为只读查询，跨模块直查 Mapper（不注入其他 Service，避免循环依赖）。
 * 所有查询必带 {@code couple_id = UserContext.getCoupleId()}（数据隔离铁律，00-全局约定.md §3）。
 *
 * <p>性能约定：菜品名与打卡图片一律批量 {@code IN} 查询后在内存归组，杜绝 N+1
 * （一页 20 条只发 4 次查询：约定 / 菜品 / 打卡 / 首图）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 每页条数上限 */
    private static final int MAX_PAGE_SIZE = 50;
    /** 周历可前后翻的周数上限，超出返回空周 */
    private static final int MAX_WEEK_OFFSET = 4;
    /** 最爱菜取前 N 道 */
    private static final int TOP_DISH_LIMIT = 3;

    private static final String ROLE_ORDERER = "orderer";
    private static final String ROLE_CARRIER = "carrier";
    private static final String SCOPE_MONTH = "month";
    private static final String SCOPE_YEAR = "year";

    private static final DateTimeFormatter MONTH_ANCHOR = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MealPlanMapper mealPlanMapper;
    private final MealPlanItemMapper mealPlanItemMapper;
    private final MealCheckinMapper mealCheckinMapper;
    private final MealCheckinImageMapper mealCheckinImageMapper;
    private final LoveValueMapper loveValueMapper;
    private final CoupleMapper coupleMapper;
    private final UserMapper userMapper;

    // ===================== 历史列表 =====================

    @Override
    public PageResult<HistoryItem> history(int pageNo, int pageSize, List<Integer> statuses, String role) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();

        long current = pageNo <= 0 ? 1L : pageNo;
        long size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        List<Integer> statusList = normalizeStatuses(statuses);
        String roleFilter = normalizeRole(role);

        // 今天的不进历史（避免与今日页重复）；排序 plan_date DESC + id DESC，保证同天多条稳定分页
        LambdaQueryWrapper<MealPlan> qw = new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getCoupleId, coupleId)
                .lt(MealPlan::getPlanDate, LocalDate.now())
                .in(!statusList.isEmpty(), MealPlan::getStatus, statusList)
                .orderByDesc(MealPlan::getPlanDate)
                .orderByDesc(MealPlan::getId);
        if (ROLE_ORDERER.equals(roleFilter)) {
            qw.eq(MealPlan::getOrdererId, me);
        } else if (ROLE_CARRIER.equals(roleFilter)) {
            qw.eq(MealPlan::getCarrierId, me);
        }

        Page<MealPlan> page = mealPlanMapper.selectPage(new Page<>(current, size), qw);
        List<MealPlan> rows = page.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), List.of());
        }

        List<Long> planIds = rows.stream().map(MealPlan::getId).toList();
        Map<Long, List<String>> dishMap = loadDishNames(planIds);
        Map<Long, MealCheckin> checkinMap = loadCheckins(coupleId, planIds);
        Map<Long, String> thumbMap = loadFirstThumbs(
                checkinMap.values().stream().map(MealCheckin::getId).toList());
        String partnerNickname = loadPartnerNickname(coupleId, me);

        List<HistoryItem> items = new ArrayList<>(rows.size());
        for (MealPlan p : rows) {
            MealCheckin checkin = checkinMap.get(p.getId());
            HistoryItem item = new HistoryItem();
            item.setId(p.getId());
            item.setPlanDate(p.getPlanDate());
            item.setMonth(p.getPlanDate() == null ? null : MONTH_ANCHOR.format(p.getPlanDate()));
            item.setStatus(p.getStatus());
            item.setRole(me.equals(p.getOrdererId()) ? ROLE_ORDERER : ROLE_CARRIER);
            item.setItems(dishMap.getOrDefault(p.getId(), List.of()));
            item.setPartnerNickname(partnerNickname);
            item.setHasCheckin(checkin != null);
            item.setCheckinRating(checkin == null ? null : checkin.getRating());
            item.setThumbUrl(checkin == null ? null : thumbMap.get(checkin.getId()));
            items.add(item);
        }
        return PageResult.of(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), items);
    }

    // ===================== 周历 =====================

    @Override
    public WeekVO week(LocalDate startDate) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 传入任意日期都对齐到那一周的周一
        LocalDate weekStart = startDate == null
                ? thisMonday
                : startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // 只允许前后翻 ±4 周，超出返回空周（7 天仍在，plans 为空），不报错
        boolean outOfRange = Math.abs(ChronoUnit.WEEKS.between(thisMonday, weekStart)) > MAX_WEEK_OFFSET;
        List<MealPlan> plans;
        if (outOfRange) {
            plans = List.of();
        } else {
            // 一次查全周，内存里按 plan_date 分组（逐天查会 7 次往返）
            plans = mealPlanMapper.selectList(new LambdaQueryWrapper<MealPlan>()
                    .eq(MealPlan::getCoupleId, coupleId)
                    .between(MealPlan::getPlanDate, weekStart, weekStart.plusDays(6))
                    .orderByAsc(MealPlan::getPlanDate)
                    .orderByAsc(MealPlan::getId));
        }

        Map<LocalDate, List<MealPlan>> byDate = new LinkedHashMap<>();
        for (MealPlan p : plans) {
            byDate.computeIfAbsent(p.getPlanDate(), k -> new ArrayList<>()).add(p);
        }
        Map<Long, List<String>> dishMap = loadDishNames(plans.stream().map(MealPlan::getId).toList());

        WeekVO vo = new WeekVO();
        vo.setWeekStart(weekStart);
        List<WeekVO.Day> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            WeekVO.Day day = new WeekVO.Day();
            day.setDate(date);
            day.setWeekday(date.getDayOfWeek().getValue());
            day.setIsWorkday(WorkdayUtil.isWorkday(date));   // 周六日 false -> 前端显示「休息」
            day.setIsToday(date.equals(today));

            List<WeekVO.Plan> dayPlans = new ArrayList<>();
            for (MealPlan p : byDate.getOrDefault(date, List.of())) {
                WeekVO.Plan wp = new WeekVO.Plan();
                wp.setId(p.getId());
                wp.setStatus(p.getStatus());
                wp.setRole(me.equals(p.getOrdererId()) ? ROLE_ORDERER : ROLE_CARRIER);
                wp.setItems(dishMap.getOrDefault(p.getId(), List.of()));
                dayPlans.add(wp);
            }
            day.setPlans(dayPlans);
            days.add(day);
        }
        vo.setDays(days);
        return vo;
    }

    // ===================== 报告（月报 / 年报） =====================

    @Override
    public ReportVO report(String scope, String anchor) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();

        if (!SCOPE_MONTH.equals(scope) && !SCOPE_YEAR.equals(scope)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scope 只能是 month 或 year");
        }
        boolean year = SCOPE_YEAR.equals(scope);
        LocalDate start;
        LocalDate end;
        if (year) {
            int y = parseYearAnchor(anchor);
            start = LocalDate.of(y, 1, 1);
            end = LocalDate.of(y, 12, 31);
        } else {
            YearMonth ym;
            if (anchor == null || anchor.isBlank()) {
                ym = YearMonth.now().minusMonths(1);          // 缺省：上一自然月
            } else {
                try {
                    ym = YearMonth.parse(anchor.trim(), MONTH_ANCHOR);
                } catch (DateTimeParseException e) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "anchor 需形如 2026-09");
                }
            }
            start = ym.atDay(1);
            end = ym.atEndOfMonth();
        }

        ReportVO vo = new ReportVO();
        vo.setScope(scope);
        vo.setAnchor(year ? String.valueOf(start.getYear()) : MONTH_ANCHOR.format(start));
        vo.setStartDate(start);
        vo.setEndDate(end);
        vo.setCheckinDays(0);
        vo.setLoveValueDelta(0);
        vo.setLongestStreak(null);
        vo.setPartnerNickname(loadPartnerNickname(coupleId, me));

        // 区间内全部约定：status=4 的用于次数统计，全量用于「打卡天数」回查 plan_date
        List<MealPlan> plans = mealPlanMapper.selectList(new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getCoupleId, coupleId)
                .between(MealPlan::getPlanDate, start, end));
        List<MealPlan> completed = plans.stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == PlanStatus.COMPLETED.getCode())
                .toList();
        vo.setPlanCount(completed.size());

        // 空期间不造数据：直接返回空报告（前端整卡不渲染）
        if (completed.isEmpty()) {
            return vo;
        }

        Long partnerId = partnerId(coupleId, me);
        ReportVO.CarrierCount carrier = new ReportVO.CarrierCount();
        int meCount = 0;
        int partnerCount = 0;
        Set<LocalDate> completedDates = new TreeSet<>();
        for (MealPlan p : completed) {
            if (me.equals(p.getCarrierId())) {
                meCount++;
            } else if (partnerId != null && partnerId.equals(p.getCarrierId())) {
                partnerCount++;
            }
            if (p.getPlanDate() != null) {
                completedDates.add(p.getPlanDate());
            }
        }
        carrier.setMe(meCount);
        carrier.setPartner(partnerCount);
        vo.setCarrierCount(carrier);

        vo.setTopDishes(topDishes(completed.stream().map(MealPlan::getId).toList()));
        vo.setCheckinDays(countCheckinDays(coupleId, plans));
        vo.setLoveValueDelta(sumLoveValue(coupleId, start, end));
        // 最长连续仅年报：周末被跳过仍算连续，中间夹着工作日没带饭则断
        vo.setLongestStreak(year ? longestStreak(completedDates) : null);
        return vo;
    }

    // ===================== 私有工具 =====================

    /** 状态入参校验：只收 0–6，去重后返回；空数组等同不筛 */
    private List<Integer> normalizeStatuses(List<Integer> statuses) {
        if (CollectionUtils.isEmpty(statuses)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>(statuses.size());
        for (Integer s : statuses) {
            if (s == null) {
                continue;
            }
            PlanStatus.of(s);                     // 非法状态抛 PARAM_INVALID
            if (!result.contains(s)) {
                result.add(s);
            }
        }
        return result;
    }

    /** 角色入参校验：orderer / carrier，其余（含 null / all）视为不筛 */
    private String normalizeRole(String role) {
        if (role == null || role.isBlank() || "all".equalsIgnoreCase(role)) {
            return null;
        }
        String r = role.trim();
        if (!ROLE_ORDERER.equals(r) && !ROLE_CARRIER.equals(r)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "role 只能是 orderer 或 carrier");
        }
        return r;
    }

    private int parseYearAnchor(String anchor) {
        String text;
        if (anchor == null || anchor.isBlank()) {
            text = String.valueOf(LocalDate.now().getYear());   // 缺省：当年
        } else {
            text = anchor.trim();
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "年报 anchor 需形如 2026");
        }
    }

    /** 批量查菜品名快照（按 planId 归组，按 seq 升序），避免 N+1 */
    private Map<Long, List<String>> loadDishNames(Collection<Long> planIds) {
        if (CollectionUtils.isEmpty(planIds)) {
            return Map.of();
        }
        List<MealPlanItem> items = mealPlanItemMapper.selectList(new LambdaQueryWrapper<MealPlanItem>()
                .in(MealPlanItem::getPlanId, planIds)
                .orderByAsc(MealPlanItem::getPlanId)
                .orderByAsc(MealPlanItem::getSeq));
        Map<Long, List<String>> map = new HashMap<>();
        for (MealPlanItem it : items) {
            map.computeIfAbsent(it.getPlanId(), k -> new ArrayList<>()).add(it.getName());
        }
        return map;
    }

    /** 批量查打卡主体（uk_meal_checkin_plan：一约定最多一条），按 planId 归组 */
    private Map<Long, MealCheckin> loadCheckins(Long coupleId, Collection<Long> planIds) {
        if (CollectionUtils.isEmpty(planIds)) {
            return Map.of();
        }
        List<MealCheckin> list = mealCheckinMapper.selectList(new LambdaQueryWrapper<MealCheckin>()
                .eq(MealCheckin::getCoupleId, coupleId)
                .in(MealCheckin::getPlanId, planIds));
        Map<Long, MealCheckin> map = new HashMap<>();
        for (MealCheckin c : list) {
            map.put(c.getPlanId(), c);
        }
        return map;
    }

    /**
     * 批量取打卡首图缩略图：审核通过（audit_status=1）中 seq 最小的那张。
     * 按 seq 升序遍历，每个 checkinId 只取第一次命中的记录。
     *
     * @return {@code checkinId -> thumbUrl}
     */
    private Map<Long, String> loadFirstThumbs(Collection<Long> checkinIds) {
        if (CollectionUtils.isEmpty(checkinIds)) {
            return Map.of();
        }
        List<MealCheckinImage> images = mealCheckinImageMapper.selectList(
                new LambdaQueryWrapper<MealCheckinImage>()
                        .in(MealCheckinImage::getCheckinId, checkinIds)
                        .eq(MealCheckinImage::getAuditStatus, AuditStatus.PASSED.getCode())
                        .orderByAsc(MealCheckinImage::getSeq)
                        .orderByAsc(MealCheckinImage::getId));
        Map<Long, String> map = new HashMap<>();
        for (MealCheckinImage img : images) {
            map.putIfAbsent(img.getCheckinId(), img.getThumbUrl());
        }
        return map;
    }

    /** 最爱菜 top3：按菜名出现次数倒序，同次数按菜名升序保证稳定 */
    private List<ReportVO.DishCount> topDishes(Collection<Long> planIds) {
        if (CollectionUtils.isEmpty(planIds)) {
            return List.of();
        }
        List<MealPlanItem> items = mealPlanItemMapper.selectList(
                new LambdaQueryWrapper<MealPlanItem>().in(MealPlanItem::getPlanId, planIds));
        Map<String, Integer> counter = new HashMap<>();
        for (MealPlanItem it : items) {
            if (it.getName() == null || it.getName().isBlank()) {
                continue;
            }
            counter.merge(it.getName(), 1, Integer::sum);
        }
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_DISH_LIMIT)
                .map(e -> {
                    ReportVO.DishCount d = new ReportVO.DishCount();
                    d.setName(e.getKey());
                    d.setCount(e.getValue());
                    return d;
                })
                .toList();
    }

    /** 有打卡的天数：同一天多次只算一天 */
    private int countCheckinDays(Long coupleId, List<MealPlan> plans) {
        if (plans.isEmpty()) {
            return 0;
        }
        Map<Long, LocalDate> dateOfPlan = new HashMap<>();
        for (MealPlan p : plans) {
            dateOfPlan.put(p.getId(), p.getPlanDate());
        }
        List<MealCheckin> checkins = mealCheckinMapper.selectList(new LambdaQueryWrapper<MealCheckin>()
                .eq(MealCheckin::getCoupleId, coupleId)
                .in(MealCheckin::getPlanId, dateOfPlan.keySet()));
        Set<LocalDate> days = new HashSet<>();
        for (MealCheckin c : checkins) {
            LocalDate d = dateOfPlan.get(c.getPlanId());
            if (d != null) {
                days.add(d);
            }
        }
        return days.size();
    }

    /** 期间爱心值增量：按 love_value.create_time 落在 [start 00:00, end 23:59:59] 统计 */
    private int sumLoveValue(Long coupleId, LocalDate start, LocalDate end) {
        List<LoveValue> list = loveValueMapper.selectList(new LambdaQueryWrapper<LoveValue>()
                .eq(LoveValue::getCoupleId, coupleId)
                .ge(LoveValue::getCreateTime, start.atStartOfDay())
                .le(LoveValue::getCreateTime, end.atTime(LocalTime.MAX)));
        int sum = 0;
        for (LoveValue v : list) {
            sum += v.getDelta() == null ? 0 : v.getDelta();
        }
        return sum;
    }

    /**
     * 最长连续带饭天数（Java 遍历，不写 SQL）：相邻两天只隔周末仍算连续，
     * 中间夹着任何一个工作日没带饭就断。
     */
    private int longestStreak(Set<LocalDate> dates) {
        if (dates.size() <= 1) {
            return dates.size();
        }
        int best = 0;
        int cur = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            if (prev == null || d.equals(prev.plusDays(1)) || onlyWeekendBetween(prev, d)) {
                cur++;
            } else {
                cur = 1;
            }
            best = Math.max(best, cur);
            prev = d;
        }
        return best;
    }

    /** 两个带饭日之间的空档是否全是周末 */
    private boolean onlyWeekendBetween(LocalDate from, LocalDate to) {
        for (LocalDate d = from.plusDays(1); d.isBefore(to); d = d.plusDays(1)) {
            if (WorkdayUtil.isWorkday(d)) {
                return false;
            }
        }
        return true;
    }

    /** 对方 userId；关系不存在或已解绑抛 NO_COUPLE */
    private Long partnerId(Long coupleId, Long me) {
        Couple c = coupleMapper.selectById(coupleId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        return me.equals(c.getUserA()) ? c.getUserB() : c.getUserA();
    }

    /** 对方昵称（user.nickname），查不到为 null */
    private String loadPartnerNickname(Long coupleId, Long me) {
        Long partnerId = partnerId(coupleId, me);
        if (partnerId == null) {
            return null;
        }
        User u = userMapper.selectById(partnerId);
        return u == null ? null : u.getNickname();
    }
}
