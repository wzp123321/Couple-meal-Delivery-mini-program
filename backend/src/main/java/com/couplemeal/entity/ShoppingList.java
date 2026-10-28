package com.couplemeal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采购清单，对应 shopping_list。
 * plan_id + item_name 唯一，重复生成不产生重复条目。
 */
@Data
@TableName("shopping_list")
public class ShoppingList implements Serializable {

    /** 主键（53bit 雪花 ID） */
    private Long id;

    /** 关联约定 */
    private Long planId;

    /** 关系隔离键 */
    private Long coupleId;

    /** 食材 / 商品 */
    private String itemName;

    /** 0未买 1已买 */
    private Integer checkedFlag;

    /** 勾选时间 */
    private LocalDateTime checkedTime;

    /** 排序 */
    private Integer seq;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
