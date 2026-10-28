package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 关系邀请码（6 位纯数字、24h 有效、一次性，限频见 10-关系绑定/后端.md §3.2）。
 * 见 db/schema.sql couple_invite 表。
 */
@Data
@TableName("couple_invite")
public class CoupleInvite implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 6 位数字邀请码，uk_couple_invite_code 唯一 */
    private String code;

    /** 发起方 user_id */
    private Long creatorId;

    /** 0待使用 1已使用 2已过期 3已作废（见 InviteStatus） */
    private Integer status;

    /** 过期时间 = 创建 + 24h */
    private LocalDateTime expireTime;

    /** 加入方 user_id */
    private Long usedBy;

    private LocalDateTime usedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
