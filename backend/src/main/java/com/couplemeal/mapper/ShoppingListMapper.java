package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.ShoppingList;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购清单 Mapper（shopping_list）。
 */
@Mapper
public interface ShoppingListMapper extends BaseMapper<ShoppingList> {
}
