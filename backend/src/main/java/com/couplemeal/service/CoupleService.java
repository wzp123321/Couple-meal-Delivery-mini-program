package com.couplemeal.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 关系服务：coupleId 解析（含 60s 本地缓存）+ 邀请码/加入/关系主页/设置/解绑。
 */
public interface CoupleService {

    /**
     * 解析用户当前所属的有效关系 id。无有效关系返回 null。
     * 带 60 秒本地缓存（见 00-全局约定.md §3，避免每次请求都查库）。
     */
    Long resolveCoupleId(Long userId);

    /** 主动失效某用户的 coupleId 缓存（解绑/绑定/注销后调用） */
    void evictCache(Long userId);

    /** 生成邀请码（限频/一次性/24h） */
    InviteResp generateInvite();

    /** 凭码加入（全程事务，并发由 select for update + 唯一约束兜底，解绑后复活） */
    JoinResp join(String code);

    /** 关系主页数据 */
    CoupleInfoResp info();

    /** 关系设置（纪念日 / 主题 / 专属称呼） */
    void settings(SettingsReq req);

    /** 解绑（逻辑删除，记录保留） */
    void unbind();

    // ===== 请求/响应对象（就近放置，不设 dto 包） =====

    class InviteResp {
        public String code;
        public LocalDateTime expireAt;
    }

    class JoinResp {
        public Long coupleId;
        public Partner partner;
    }

    class Partner {
        public Long userId;
        public String nickname;
        public String avatar;
    }

    /**
     * 关系主页数据。字段一律按**调用者视角**（my / partner）返回，
     * 不暴露 user_a / user_b 的中性排序——前端拿不到也不需要"哪个是 A"。
     */
    class CoupleInfoResp {
        public Long coupleId;
        public String myAvatar;
        public String myNickname;
        public String partnerAvatar;
        public String partnerNickname;
        public String nicknameMine;      // 我对对方的专属称呼
        public String nicknamePartner;   // 对方对我的专属称呼
        public Integer togetherDays;     // 纪念日优先，否则绑定日
        public Integer mealDays;         // 一起吃饭天数（打卡去重），与 togetherDays 是两个指标
        public Integer myLove;
        public Integer partnerLove;
        public LocalDate anniversary;
        public String theme;
        public Boolean remindEnabled;
    }

    class SettingsReq {
        public LocalDate anniversary;
        public String theme;
        public Boolean remindEnabled;
        public String nicknameMine;   // 专属称呼（关系级，非用户昵称）
    }
}
