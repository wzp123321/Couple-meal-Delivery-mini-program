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

    /**
     * 按规范化对（user_a < user_b）查历史记录（含已解绑），用于加入时「复活原记录」（D6）。
     */
    @Select("SELECT * FROM couple WHERE user_a = #{userA} AND user_b = #{userB} LIMIT 1")
    Couple selectByPair(@Param("userA") Long userA, @Param("userB") Long userB);

    /**
     * 在一起天数：优先纪念日，未设置回落到绑定日（见 后端.md §3.7）。
     */
    @Select("SELECT DATEDIFF(CURDATE(), COALESCE(anniversary, DATE(create_time))) + 1 " +
            "FROM couple WHERE id = #{coupleId} AND status = 1")
    int countTogetherDays(@Param("coupleId") Long coupleId);

    /**
     * 一起吃饭天数：有打卡的日期去重（累计值，见 后端.md §3.7）。
     */
    @Select("SELECT COUNT(DISTINCT p.plan_date) FROM meal_checkin c " +
            "JOIN meal_plan p ON p.id = c.plan_id WHERE p.couple_id = #{coupleId}")
    int countMealDays(@Param("coupleId") Long coupleId);
}
