package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.LoveValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 爱心值 Mapper（仅暴露关系主页所需的累计求和，见 后端.md §3.7）。
 * 完整爱心值业务逻辑（award / 幂等）属模块 40，不在 M1 范围。
 */
@Mapper
public interface LoveValueMapper extends BaseMapper<LoveValue> {

    /** 某关系下某用户的累计爱心值（SUM(delta)，无记录返回 0） */
    @Select("SELECT IFNULL(SUM(delta), 0) FROM love_value " +
            "WHERE couple_id = #{coupleId} AND user_id = #{userId}")
    int sumByUser(@Param("coupleId") Long coupleId,
                  @Param("userId") Long userId);
}
