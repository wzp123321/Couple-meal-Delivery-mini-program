package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.Couple;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 关系 Mapper。仅提供 M0 所需的有效关系查询。
 */
@Mapper
public interface CoupleMapper extends BaseMapper<Couple> {

    /**
     * 查某用户当前有效（status=1）的关系。
     * 用户可能是 user_a 或 user_b，故 OR 命中；一对只一条，LIMIT 1。
     */
    @Select("SELECT id, user_a, user_b, status FROM couple " +
            "WHERE (user_a = #{userId} OR user_b = #{userId}) AND status = 1 LIMIT 1")
    Couple selectValidByUserId(@Param("userId") Long userId);
}
