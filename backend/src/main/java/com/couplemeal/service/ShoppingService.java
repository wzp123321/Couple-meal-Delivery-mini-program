package com.couplemeal.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购清单：按约定生成（幂等）、查询、勾选。
 *
 * <p>生成走 {@code uk_shopping_list_plan_item(plan_id, item_name)} 唯一索引兜底：
 * 逐条 insert，撞 DuplicateKeyException 视为成功并跳过，不做「先查再插」（并发会翻倍，见 01-通用机制.md §2）。
 */
public interface ShoppingService {

    /**
     * 生成（幂等）约定采购清单，返回生成后的完整清单。
     *
     * @param planId 约定 id，必须属于当前关系（否则 1002）
     * @param items  显式食材名；为空时由约定菜品推导
     */
    List<ItemVO> generate(Long planId, List<String> items);

    /**
     * 查询清单。
     *
     * @param planId 传则查该约定的清单；传 null 取「最近一条有未买条目」的清单
     */
    List<ItemVO> list(Long planId);

    /**
     * 勾选 / 取消勾选。取消勾选时 checked_time 置回 null（否则排序错乱）。
     *
     * @param checkedFlag 0 未买 / 1 已买
     */
    void check(Long id, Integer checkedFlag);

    /** 清单条目。已买条目照常返回，由前端置底、灰色展示，不删除。 */
    class ItemVO {
        public Long id;
        public Long planId;
        public String itemName;
        public Integer checkedFlag;
        public LocalDateTime checkedTime;
        public Integer seq;
    }
}
