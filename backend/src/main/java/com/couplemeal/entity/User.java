package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户（微信身份）。见 db/schema.sql + 00-全局约定.md §2。
 *
 * <p>注意：`user` 是 MySQL 保留字，表名必须加反引号（schema.sql 未加，属 CCR 候选；
 * 此处以 @TableName("`user`") 规避，使现有 DDL 可落地）。
 */
@Data
@TableName("`user`")
public class User implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 微信 openid，登录唯一身份 */
    private String openid;

    /** 昵称，用户自行填写 */
    private String nickname;

    /** 头像相对路径 /upload/avatar/... */
    private String avatar;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
