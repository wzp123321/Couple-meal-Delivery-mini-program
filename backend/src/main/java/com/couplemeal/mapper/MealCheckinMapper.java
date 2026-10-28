package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.MealCheckin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 打卡主体 Mapper（仅暴露账号注销所需清理，见 后端.md §3.5）。
 * 完整打卡业务逻辑属模块 60，不在 M1 范围。
 */
@Mapper
public interface MealCheckinMapper extends BaseMapper<MealCheckin> {

    /** 删除本人的打卡主体记录（先删 meal_checkin_image 子表，再调本方法） */
    @Select("DELETE FROM meal_checkin WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);
}
