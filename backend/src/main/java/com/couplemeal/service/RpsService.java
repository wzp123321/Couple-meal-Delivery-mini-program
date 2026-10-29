package com.couplemeal.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 石头剪刀布协商服务（phase-2，见 openspec/changes/phase-2/specs/rps-negotiation）。
 *
 * <p>规则要点：
 *   - best-of-1，平局重出（current_round 自增），直至分出胜负；
 *   - 对局仅双方可见，同一 (couple, date, meal_type) 进行中的对局幂等复用；
 *   - 「采纳」才生成约定：输家为带饭方（source=1，待接收），保留用户最终决定权。
 */
public interface RpsService {

    /** 发起 / 复用一局（幂等）。返回对局视图。 */
    RpsGameVO create(int mealType, LocalDate planDate);

    /** 当前用户对局出拳。返回出拳后的对局视图。 */
    RpsGameVO throwChoice(long gameId, int choice);

    /** 采纳结果，生成「输家主动带」约定，返回 planId（幂等）。 */
    Long adopt(long gameId, List<ItemInput> items, String remark);

    /** 按 id 取对局视图。 */
    RpsGameVO get(long gameId);

    /** 取 (couple, date, meal_type) 进行中的对局视图，无则返回 null。 */
    RpsGameVO current(int mealType, LocalDate planDate);

    // ===== 出参 / 入参（就近放置） =====

    /** 出拳选项：1石头 2剪刀 3布 */
    int ROCK = 1;
    int SCISSORS = 2;
    int PAPER = 3;

    /** 对局视图 */
    class RpsGameVO {
        public Long gameId;
        public Long coupleId;
        public LocalDate planDate;
        public Integer mealType;
        public Long initiatorId;
        /** 0 进行中 / 1 已完成 */
        public Integer status;
        public Long winnerId;
        public Long planId;
        /** 当前回合号 */
        public Integer currentRound;
        /** 当前回合「我」的出拳（未出为 null） */
        public Integer myChoice;
        /** 当前回合对方是否已出拳 */
        public Boolean opponentThrown;
    }

    /** 采纳时可选的菜品（留空则给一个默认占位名） */
    class ItemInput {
        public Long menuItemId;
        public String name;
    }
}
