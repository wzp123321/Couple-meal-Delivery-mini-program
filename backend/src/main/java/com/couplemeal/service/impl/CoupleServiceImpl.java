package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.domain.enums.InviteStatus;
import com.couplemeal.domain.enums.PlanStatus;
import com.couplemeal.entity.Couple;
import com.couplemeal.entity.CoupleInvite;
import com.couplemeal.entity.MealPlan;
import com.couplemeal.entity.User;
import com.couplemeal.mapper.CoupleInviteMapper;
import com.couplemeal.mapper.CoupleMapper;
import com.couplemeal.mapper.MealPlanMapper;
import com.couplemeal.mapper.UserMapper;
import com.couplemeal.service.CoupleService;
import com.couplemeal.service.LoveValueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关系服务实现：coupleId 解析 + 60 秒本地缓存 + 关系建立/维系/终止。
 *
 * <p>设计要点（见 后端.md §3）：
 *   - 邀请码限频（idx_couple_invite_creator_time）、撞码重试（uk_couple_invite_code）。
 *   - 加入全程一个事务，select for update 锁码 + 唯一关系校验；解绑后复合「复活」原记录（D6）。
 *   - 解绑逻辑删除；注销由 AuthService 负责（含解绑）。
 */
@Slf4j
@Service
public class CoupleServiceImpl implements CoupleService {

    private static final long CACHE_TTL_MS = 60_000L;
    private static final int INVITE_FREQUENCY_LIMIT = 5;     // 每小时上限 5 次
    private static final int INVITE_CODE_RETRY = 5;          // 撞码最多重试 5 次
    private static final String DEFAULT_THEME = "cream";     // 默认主题色（schema.sql 默认值）
    /** 对方已注销时 user 行不在了，历史展示用的占位名（后端.md §3.5） */
    private static final String DELETED_USER_NAME = "已注销用户";

    private final CoupleMapper coupleMapper;
    private final CoupleInviteMapper coupleInviteMapper;
    private final UserMapper userMapper;
    private final MealPlanMapper mealPlanMapper;
    private final LoveValueService loveValueService;

    /** userId -> (coupleId, 写入时间戳)；coupleId 可能为 null */
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public CoupleServiceImpl(CoupleMapper coupleMapper, CoupleInviteMapper coupleInviteMapper,
                             UserMapper userMapper, MealPlanMapper mealPlanMapper,
                             LoveValueService loveValueService) {
        this.coupleMapper = coupleMapper;
        this.coupleInviteMapper = coupleInviteMapper;
        this.userMapper = userMapper;
        this.mealPlanMapper = mealPlanMapper;
        this.loveValueService = loveValueService;
    }

    @Override
    public Long resolveCoupleId(Long userId) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(userId);
        if (entry != null && (now - entry.ts) < CACHE_TTL_MS) {
            return entry.coupleId;
        }
        Couple couple = coupleMapper.selectValidByUserId(userId);
        Long coupleId = couple != null ? couple.getId() : null;
        cache.put(userId, new CacheEntry(coupleId, now));
        return coupleId;
    }

    @Override
    public void evictCache(Long userId) {
        cache.remove(userId);
    }

    // ===================== 生成邀请码 =====================

    @Override
    @Transactional
    public InviteResp generateInvite() {
        Long me = currentUserId();
        if (coupleMapper.selectValidByUserId(me) != null) {
            throw new BizException(ErrorCode.ALREADY_BOUND);
        }
        // 限频：近 1 小时生成次数 ≥ 5 → 1006
        int recent = coupleInviteMapper.countRecentByCreator(me, LocalDateTime.now().minusHours(1));
        if (recent >= INVITE_FREQUENCY_LIMIT) {
            throw new BizException(ErrorCode.INVITE_TOO_FREQUENT);
        }
        CoupleInvite invite = new CoupleInvite();
        invite.setId(IdUtil.nextId());
        invite.setCreatorId(me);
        invite.setStatus(InviteStatus.PENDING.code());
        invite.setExpireTime(LocalDateTime.now().plusHours(24));
        invite.setCode(generateCode());
        for (int i = 0; i < INVITE_CODE_RETRY; i++) {
            try {
                coupleInviteMapper.insert(invite);
                break;
            } catch (DuplicateKeyException e) {
                if (i == INVITE_CODE_RETRY - 1) {
                    log.error("邀请码撞码重试耗尽 creatorId={}", me);
                    throw new BizException(ErrorCode.SYS_ERROR);
                }
                invite.setCode(generateCode());
            }
        }
        InviteResp resp = new InviteResp();
        resp.code = invite.getCode();
        resp.expireAt = invite.getExpireTime();
        return resp;
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    // ===================== 加入 =====================

    @Override
    @Transactional
    public JoinResp join(String code) {
        Long me = currentUserId();
        if (coupleMapper.selectValidByUserId(me) != null) {
            throw new BizException(ErrorCode.ALREADY_BOUND);
        }
        // 锁住该码，防两人同时用同一码
        CoupleInvite invite = coupleInviteMapper.selectByCodeForUpdate(code);
        if (invite == null
                || invite.getStatus() != InviteStatus.PENDING.code()
                || invite.getExpireTime().isBefore(LocalDateTime.now())) {
            // 不区分「过期/已用/不存在」，统一 1003
            throw new BizException(ErrorCode.INVITE_INVALID);
        }
        Long creator = invite.getCreatorId();
        if (creator.equals(me)) {
            throw new BizException(ErrorCode.CANNOT_BIND_SELF);
        }
        if (coupleMapper.selectValidByUserId(creator) != null) {
            throw new BizException(ErrorCode.ALREADY_BOUND);
        }
        Long a = Math.min(me, creator);
        Long b = Math.max(me, creator);
        // 先查历史记录决定「复活 / 新建」（D6：解绑后复合不能 insert，否则撞 uk_couple_pair）
        Couple existing = coupleMapper.selectByPair(a, b);
        Long coupleId;
        if (existing != null) {
            existing.setStatus(1);
            existing.setUnboundTime(null);
            coupleMapper.updateById(existing);
            coupleId = existing.getId();
        } else {
            Couple c = new Couple();
            c.setId(IdUtil.nextId());
            c.setUserA(a);
            c.setUserB(b);
            c.setTheme(DEFAULT_THEME);
            c.setStatus(1);
            try {
                coupleMapper.insert(c);
                coupleId = c.getId();
            } catch (DuplicateKeyException e) {
                // 并发兜底：双方各自拿着不同邀请码同时加入，唯一索引拒掉后到者，复用已落库的那条
                Couple persisted = coupleMapper.selectByPair(a, b);
                if (persisted == null) {
                    throw e;
                }
                persisted.setStatus(1);
                persisted.setUnboundTime(null);
                coupleMapper.updateById(persisted);
                coupleId = persisted.getId();
            }
        }
        // 码置为已使用
        invite.setStatus(InviteStatus.USED.code());
        invite.setUsedBy(me);
        invite.setUsedTime(LocalDateTime.now());
        coupleInviteMapper.updateById(invite);

        cache.remove(me);
        cache.remove(creator);

        JoinResp resp = new JoinResp();
        resp.coupleId = coupleId;
        // 发起方理论上一定在（注销会先把未使用的码作废），缺行数时绝不 NPE
        User partner = userMapper.selectById(creator);
        Partner p = new Partner();
        p.userId = creator;
        if (partner != null) {
            p.nickname = partner.getNickname();
            p.avatar = partner.getAvatar();
        }
        resp.partner = p;
        return resp;
    }

    // ===================== 关系主页 =====================

    @Override
    public CoupleInfoResp info() {
        Long cid = UserContext.requireCoupleId();
        Couple c = coupleMapper.selectById(cid);
        if (c == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        Long me = currentUserId();
        boolean iAmA = me.equals(c.getUserA());
        User my = userMapper.selectById(iAmA ? c.getUserA() : c.getUserB());
        User partner = userMapper.selectById(iAmA ? c.getUserB() : c.getUserA());
        if (my == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }

        CoupleInfoResp resp = new CoupleInfoResp();
        resp.coupleId = cid;
        resp.myAvatar = my.getAvatar();
        resp.myNickname = my.getNickname();
        // 对方账号已注销时 user 行不在了，历史仍要约得出来，展示占位名而不是报错
        resp.partnerAvatar = partner == null ? null : partner.getAvatar();
        resp.partnerNickname = partner == null ? DELETED_USER_NAME : partner.getNickname();
        resp.nicknameMine = iAmA ? c.getNicknameA() : c.getNicknameB();
        resp.nicknamePartner = iAmA ? c.getNicknameB() : c.getNicknameA();
        // 在一起天数：纪念日优先，未设置回落到建关系当天；用 MySQL 的 DATEDIFF 口径避免时区偏差
        resp.togetherDays = coupleMapper.countTogetherDays(cid);
        // 一起吃饭天数：累计值，与 togetherDays 是两个指标（见 后端.md §3.7）
        resp.mealDays = countMealDays(cid, me);
        LoveValueService.Summary love = loveValueService.summary(cid, me);
        resp.myLove = toInt(love.getMine());
        resp.partnerLove = toInt(love.getPartner());
        resp.anniversary = c.getAnniversary();
        resp.theme = c.getTheme();
        resp.remindEnabled = c.getRemindEnabled() != null && c.getRemindEnabled();
        return resp;
    }

    /**
     * 一起吃饭天数：本关系内「已完成」且本人作为点餐方/带饭方参与的约定，按约定日期去重计数。
     * 这是累计值，不加日期范围；按自然月/年的区间天数由 report 接口的 checkinDays 承担。
     */
    private int countMealDays(Long coupleId, Long me) {
        QueryWrapper<MealPlan> qw = new QueryWrapper<MealPlan>()
                .select("COUNT(DISTINCT plan_date)")
                .eq("couple_id", coupleId)
                .eq("status", PlanStatus.COMPLETED.getCode())
                .and(w -> w.eq("orderer_id", me).or().eq("carrier_id", me));
        Object first = mealPlanMapper.selectObjs(qw).stream().findFirst().orElse(null);
        return first instanceof Number n ? n.intValue() : 0;
    }

    private int toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }

    // ===================== 设置 =====================

    @Override
    @Transactional
    public void settings(SettingsReq req) {
        Long cid = UserContext.requireCoupleId();
        Couple c = coupleMapper.selectById(cid);
        if (c == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        Long me = currentUserId();
        LambdaUpdateWrapper<Couple> uw = new LambdaUpdateWrapper<Couple>().eq(Couple::getId, cid);
        boolean changed = false;
        if (req.anniversary != null) {
            uw.set(Couple::getAnniversary, req.anniversary);
            changed = true;
        }
        if (req.theme != null && !req.theme.isBlank()) {
            uw.set(Couple::getTheme, req.theme);
            changed = true;
        }
        if (req.remindEnabled != null) {
            uw.set(Couple::getRemindEnabled, req.remindEnabled);
            changed = true;
        }
        // 称呼是关系级数据（couple.nickname_a/b），与用户昵称两条线，别混
        if (req.nicknameMine != null) {
            String nickname = req.nicknameMine.trim();
            if (nickname.length() < 2 || nickname.length() > 12) {
                throw new BizException(ErrorCode.PET_NAME_INVALID);
            }
            if (me.equals(c.getUserA())) {
                uw.set(Couple::getNicknameA, nickname);
            } else {
                uw.set(Couple::getNicknameB, nickname);
            }
            changed = true;
        }
        if (changed) {
            coupleMapper.update(null, uw);
        }
    }

    // ===================== 解绑 =====================

    @Override
    @Transactional
    public void unbind() {
        Long cid = UserContext.requireCoupleId();
        Couple c = coupleMapper.selectById(cid);
        if (c == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.NO_COUPLE);
        }
        c.setStatus(0);
        c.setUnboundTime(LocalDateTime.now());
        coupleMapper.updateById(c);
        cache.remove(c.getUserA());
        cache.remove(c.getUserB());
    }

    private record CacheEntry(Long coupleId, long ts) {
    }

    /** 当前登录用户 id；未登录直接抛 1001（拦截器兜过一层，这里是双保险） */
    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        return userId;
    }
}
