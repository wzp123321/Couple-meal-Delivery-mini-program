package com.couplemeal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.couplemeal.common.BizException;
import com.couplemeal.common.ErrorCode;
import com.couplemeal.common.UserContext;
import com.couplemeal.common.util.IdUtil;
import com.couplemeal.domain.enums.MealType;
import com.couplemeal.entity.Couple;
import com.couplemeal.entity.RpsGame;
import com.couplemeal.entity.RpsRound;
import com.couplemeal.mapper.CoupleMapper;
import com.couplemeal.mapper.RpsGameMapper;
import com.couplemeal.mapper.RpsRoundMapper;
import com.couplemeal.service.PlanService;
import com.couplemeal.service.RpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 石头剪刀布协商服务实现（phase-2）。
 *
 * <p>采纳生成约定的关键点：约定要求「输家带饭」，即 carrier=输家。
 * {@link PlanService#create(int, int, LocalDate, List, String)} 以调用方身份决定 carrier，
 * 故 adopt 时把 {@link UserContext} 临时切到输家身份调用，结束再还原，保证数据隔离铁律不被破坏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RpsServiceImpl implements RpsService {

    /** 对局状态：0 进行中 / 1 已完成 */
    private static final int STATUS_PLAYING = 0;
    private static final int STATUS_FINISHED = 1;

    /** 回合结果：0 待分 / 1 A 胜 / 2 B 胜 / 3 平 */
    private static final int ROUND_PENDING = 0;
    private static final int ROUND_A_WIN = 1;
    private static final int ROUND_B_WIN = 2;
    private static final int ROUND_TIE = 3;

    private final RpsGameMapper gameMapper;
    private final RpsRoundMapper roundMapper;
    private final CoupleMapper coupleMapper;
    private final PlanService planService;

    // ===================== 发起（幂等） =====================

    @Override
    @Transactional
    public RpsGameVO create(int mealType, LocalDate planDate) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        MealType.of(mealType); // 非法餐次 -> PARAM_INVALID
        if (planDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "约定日期不能为空");
        }
        // 复用进行中的对局：同一 (couple, date, meal_type) 只应有一局
        RpsGame existing = gameMapper.selectOne(new LambdaQueryWrapper<RpsGame>()
                .eq(RpsGame::getCoupleId, coupleId)
                .eq(RpsGame::getPlanDate, planDate)
                .eq(RpsGame::getMealType, mealType)
                .eq(RpsGame::getStatus, STATUS_PLAYING)
                .last("LIMIT 1"));
        if (existing != null) {
            return toVo(existing, me);
        }
        RpsGame game = new RpsGame();
        game.setId(IdUtil.nextId());
        game.setCoupleId(coupleId);
        game.setPlanDate(planDate);
        game.setMealType(mealType);
        game.setInitiatorId(me);
        game.setStatus(STATUS_PLAYING);
        game.setCurrentRound(1);
        game.setCreateTime(LocalDateTime.now());
        game.setUpdateTime(LocalDateTime.now());
        gameMapper.insert(game);
        return toVo(game, me);
    }

    // ===================== 出拳 =====================

    @Override
    @Transactional
    public RpsGameVO throwChoice(long gameId, int choice) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        if (choice != ROCK && choice != SCISSORS && choice != PAPER) {
            throw new BizException(ErrorCode.RPS_INVALID_CHOICE);
        }
        RpsGame game = loadOwn(gameId, coupleId);
        if (game.getStatus() == STATUS_FINISHED) {
            throw new BizException(ErrorCode.RPS_GAME_FINISHED);
        }
        Couple couple = coupleMapper.selectById(coupleId);
        boolean iAmA = me.equals(couple.getUserA());
        boolean iAmB = me.equals(couple.getUserB());
        if (!iAmA && !iAmB) {
            throw new BizException(ErrorCode.NO_COUPLE, "你不在这一对里");
        }
        int roundNo = game.getCurrentRound();
        RpsRound round = roundMapper.selectOne(new LambdaQueryWrapper<RpsRound>()
                .eq(RpsRound::getGameId, gameId)
                .eq(RpsRound::getRoundNo, roundNo)
                .last("LIMIT 1"));
        boolean isNewRound = false;
        if (round == null) {
            round = new RpsRound();
            round.setGameId(gameId);
            round.setRoundNo(roundNo);
            round.setResult(ROUND_PENDING);
            round.setCreateTime(LocalDateTime.now());
            round.setUpdateTime(LocalDateTime.now());
            isNewRound = true;
        }
        if (iAmA) {
            if (round.getChoiceA() != null) {
                throw new BizException(ErrorCode.RPS_ALREADY_THROWN);
            }
            round.setChoiceA(choice);
        } else {
            if (round.getChoiceB() != null) {
                throw new BizException(ErrorCode.RPS_ALREADY_THROWN);
            }
            round.setChoiceB(choice);
        }
        // 双方都出拳才判定本回合
        if (round.getChoiceA() != null && round.getChoiceB() != null) {
            resolveRound(game, round, couple);
        }
        if (isNewRound) {
            round.setId(IdUtil.nextId());
            roundMapper.insert(round);
        } else {
            roundMapper.updateById(round);
        }
        gameMapper.updateById(game);
        return toVo(game, me);
    }

    /**
     * 判定单回合并回写 game（胜负 / 平局重出）。
     */
    private void resolveRound(RpsGame game, RpsRound round, Couple couple) {
        int a = round.getChoiceA();
        int b = round.getChoiceB();
        if (a == b) {
            round.setResult(ROUND_TIE);
            game.setCurrentRound(game.getCurrentRound() + 1); // 平局重出，保持进行中
            return;
        }
        int winnerSide = beats(a, b) ? 1 : 2; // 1=A 胜 2=B 胜
        round.setResult(winnerSide == 1 ? ROUND_A_WIN : ROUND_B_WIN);
        game.setStatus(STATUS_FINISHED);
        game.setWinnerId(winnerSide == 1 ? couple.getUserA() : couple.getUserB());
    }

    /** a 是否赢 b（石头>剪刀>布>石头） */
    private boolean beats(int a, int b) {
        return (a == ROCK && b == SCISSORS)
                || (a == SCISSORS && b == PAPER)
                || (a == PAPER && b == ROCK);
    }

    // ===================== 采纳 =====================

    @Override
    @Transactional
    public Long adopt(long gameId, List<ItemInput> items, String remark) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        RpsGame game = loadOwn(gameId, coupleId);
        if (game.getStatus() != STATUS_FINISHED) {
            throw new BizException(ErrorCode.RPS_GAME_FINISHED, "这局还没分出胜负，没法采纳");
        }
        if (game.getPlanId() != null) {
            return game.getPlanId(); // 已采纳，幂等返回
        }
        Couple couple = coupleMapper.selectById(coupleId);
        Long winner = game.getWinnerId();
        Long loser = winner.equals(couple.getUserA()) ? couple.getUserB() : couple.getUserA();

        // 菜品：留空则给一个与餐段对应的默认占位名，输家随后可在约定里调整
        List<PlanService.ItemInput> planItems;
        if (items == null || items.isEmpty()) {
            String defaultName = MealType.DINNER.getCode() == game.getMealType() ? "今晚的饭" : "今天的饭";
            PlanService.ItemInput def = new PlanService.ItemInput();
            def.name = defaultName;
            planItems = List.of(def);
        } else {
            planItems = new ArrayList<>(items.size());
            for (ItemInput in : items) {
                PlanService.ItemInput pi = new PlanService.ItemInput();
                pi.menuItemId = in.menuItemId;
                pi.name = in.name;
                planItems.add(pi);
            }
        }

        // 以输家身份发起「主动带」约定（carrier=输家），结束还原上下文
        Long oldUid = UserContext.getUserId();
        Long oldCid = UserContext.getCoupleId();
        UserContext.set(loser, coupleId);
        try {
            PlanService.CreateResult result = planService.create(
                    game.getMealType(), /* source= */ 1, game.getPlanDate(), planItems, remark);
            game.setPlanId(result.planId);
            game.setUpdateTime(LocalDateTime.now());
            gameMapper.updateById(game);
            return result.planId;
        } finally {
            UserContext.set(oldUid, oldCid);
        }
    }

    // ===================== 读 =====================

    @Override
    public RpsGameVO get(long gameId) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        return toVo(loadOwn(gameId, coupleId), me);
    }

    @Override
    public RpsGameVO current(int mealType, LocalDate planDate) {
        Long coupleId = UserContext.requireCoupleId();
        Long me = UserContext.getUserId();
        MealType.of(mealType);
        RpsGame game = gameMapper.selectOne(new LambdaQueryWrapper<RpsGame>()
                .eq(RpsGame::getCoupleId, coupleId)
                .eq(RpsGame::getPlanDate, planDate)
                .eq(RpsGame::getMealType, mealType)
                .eq(RpsGame::getStatus, STATUS_PLAYING)
                .last("LIMIT 1"));
        return game == null ? null : toVo(game, me);
    }

    // ===================== 内部 =====================

    private RpsGame loadOwn(long gameId, Long coupleId) {
        RpsGame game = gameMapper.selectById(gameId);
        if (game == null || !game.getCoupleId().equals(coupleId)) {
            throw new BizException(ErrorCode.RPS_GAME_NOT_FOUND);
        }
        return game;
    }

    private RpsGameVO toVo(RpsGame game, Long me) {
        RpsGameVO vo = new RpsGameVO();
        vo.gameId = game.getId();
        vo.coupleId = game.getCoupleId();
        vo.planDate = game.getPlanDate();
        vo.mealType = game.getMealType();
        vo.initiatorId = game.getInitiatorId();
        vo.status = game.getStatus();
        vo.winnerId = game.getWinnerId();
        vo.planId = game.getPlanId();
        vo.currentRound = game.getCurrentRound();
        // 当前回合本人出拳 + 对方是否已出
        RpsRound round = roundMapper.selectOne(new LambdaQueryWrapper<RpsRound>()
                .eq(RpsRound::getGameId, game.getId())
                .eq(RpsRound::getRoundNo, game.getCurrentRound())
                .last("LIMIT 1"));
        Couple couple = coupleMapper.selectById(game.getCoupleId());
        boolean iAmA = me.equals(couple.getUserA());
        if (round != null) {
            Integer myChoice = iAmA ? round.getChoiceA() : round.getChoiceB();
            Integer oppChoice = iAmA ? round.getChoiceB() : round.getChoiceA();
            vo.myChoice = myChoice;
            vo.opponentThrown = oppChoice != null;
        } else {
            vo.myChoice = null;
            vo.opponentThrown = false;
        }
        return vo;
    }
}
