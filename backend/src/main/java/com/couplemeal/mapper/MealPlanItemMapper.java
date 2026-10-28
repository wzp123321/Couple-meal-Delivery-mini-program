package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.MealPlanItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 约定菜品明细 Mapper（meal_plan_item）。
 */
@Mapper
public interface MealPlanItemMapper extends BaseMapper<MealPlanItem> {
}
