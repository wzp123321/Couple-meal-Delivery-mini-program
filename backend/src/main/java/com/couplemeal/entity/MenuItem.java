package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜单与忌口库，对应 menu_item。
 * 软删除：status 同时承担数据状态，所有业务查询必须带 status = 1（见 MenuStatus）。
 */
@Data
@TableName("menu_item")
public class MenuItem implements Serializable {

    /** 主键（53bit 雪花 ID） */
    private Long id;

    /** 关系隔离键 */
    private Long coupleId;

    /** 菜名 ≤20字 */
    private String name;

    /** 1荤 2素 3汤 4主食，见 MenuCategory */
    private Integer category;

    /** 0普通 1常点（接口参数 onlyFavorite） */
    private Integer favoriteFlag;

    /** 0无 1少吃 2不吃 3过敏，见 AvoidLevel（只提醒不硬拦） */
    private Integer avoidLevel;

    /** 谁加的菜（区分「我的」「TA的」） */
    private Long creatorId;

    /** 菜谱笔记 */
    private String note;

    /** 数据状态：0异常 1正常 2已删除，见 MenuStatus */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
