package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.MealPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点餐约定 Mapper（meal_plan）。
 * 复杂统计 / 条件扫描 SQL 由业务 Service 自己写，此处只提供 BaseMapper 能力。
 */
@Mapper
public interface MealPlanMapper extends BaseMapper<MealPlan> {
}
