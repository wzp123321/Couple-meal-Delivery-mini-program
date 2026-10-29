package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 石头剪刀布单回合（rps_round）。
 *
 * <p>每回合双方各出一拳：choice_a 为 couple.user_a 的出拳，choice_b 为 user_b 的出拳；
 * choice 取值 1=石头 2=剪刀 3=布。result：0 待分 / 1=A 胜 / 2=B 胜 / 3=平。
 * 平局后由 {@link RpsGame#currentRound} 自增开启新回合，旧回合保留作记录。
 */
@Data
@TableName("rps_round")
public class RpsRound implements Serializable {

    /** 主键（53bit 雪花 ID） */
    private Long id;

    /** 所属对局 */
    private Long gameId;

    /** 回合号（从 1 起，与 rps_game.current_round 对齐） */
    private Integer roundNo;

    /** user_a 出拳：1石头 2剪刀 3布，未出为 null */
    private Integer choiceA;

    /** user_b 出拳：1石头 2剪刀 3布，未出为 null */
    private Integer choiceB;

    /** 0 待分 / 1 A 胜 / 2 B 胜 / 3 平 */
    private Integer result;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
