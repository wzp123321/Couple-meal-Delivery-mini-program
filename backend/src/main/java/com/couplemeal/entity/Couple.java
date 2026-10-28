package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情侣关系（user_a < user_b 规范化，保证一对只一条有效记录）。
 * 见 docs/design/00-全局约定.md D6 + db/schema.sql。
 */
@Data
@TableName("couple")
public class Couple implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 较小 user_id */
    private Long userA;

    /** 较大 user_id */
    private Long userB;

    private String nicknameA;
    private String nicknameB;
    private LocalDate anniversary;
    private String theme;

    /** 每日提醒开关（1开 0关），见 10-关系绑定/后端.md §2 couple/settings */
    private Boolean remindEnabled;

    /** 1有效 0已解绑（逻辑删除统一用 status，不引 MP 逻辑删除） */
    private Integer status;

    private LocalDateTime unboundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
