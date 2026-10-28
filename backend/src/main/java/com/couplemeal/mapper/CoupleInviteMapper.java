package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.CoupleInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 邀请码 Mapper（见 db/schema.sql couple_invite）。
 *
 * <p>索引职责：
 *   - idx_couple_invite_creator_time(creator_id, create_time)：限频统计（countRecentByCreator）
 *   - uk_couple_invite_code：按码查询（selectByCode / selectByCodeForUpdate）
 */
@Mapper
public interface CoupleInviteMapper extends BaseMapper<CoupleInvite> {

    /**
     * 限频统计：某用户近 since 之后生成的邀请码数量（用 idx_couple_invite_creator_time）。
     */
    @Select("SELECT COUNT(*) FROM couple_invite " +
            "WHERE creator_id = #{creatorId} AND create_time > #{since}")
    int countRecentByCreator(@Param("creatorId") Long creatorId,
                             @Param("since") java.time.LocalDateTime since);

    /** 按码查询（普通读，用于过期前校验之外的一般检索） */
    @Select("SELECT * FROM couple_invite WHERE code = #{code}")
    CoupleInvite selectByCode(@Param("code") String code);

    /**
     * 按码查询并加行锁，防两人同时用同一码加入（见 后端.md §3.3）。
     */
    @Select("SELECT * FROM couple_invite WHERE code = #{code} FOR UPDATE")
    CoupleInvite selectByCodeForUpdate(@Param("code") String code);

    /**
     * 过期批处理：待使用且已过期的置为「已过期(2)」。由 InviteExpireJob 每分钟驱动。
     */
    @Update("UPDATE couple_invite SET status = 2, update_time = #{now} " +
            "WHERE status = 0 AND expire_time < #{now}")
    int expireOverdue(@Param("now") java.time.LocalDateTime now);

    /**
     * 作废某用户未使用的邀请码（注销时调用，防注销后码还能绑上）。
     */
    @Update("UPDATE couple_invite SET status = 3, update_time = #{now} " +
            "WHERE creator_id = #{creatorId} AND status = 0")
    int voidByCreator(@Param("creatorId") Long creatorId,
                      @Param("now") java.time.LocalDateTime now);
}
