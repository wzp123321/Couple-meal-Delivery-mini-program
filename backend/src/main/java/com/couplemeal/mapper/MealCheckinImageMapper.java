package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.MealCheckinImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 打卡图片明细 Mapper（仅暴露账号注销所需清理，见 后端.md §3.5）。
 * 完整图片审核业务逻辑属模块 60，不在 M1 范围。
 */
@Mapper
public interface MealCheckinImageMapper extends BaseMapper<MealCheckinImage> {

    /** 收集本人打卡图片的相对路径（url / thumb_url），注销时先库后文件删除 */
    @Select("SELECT i.url AS url, i.thumb_url AS thumb_url " +
            "FROM meal_checkin_image i JOIN meal_checkin c ON c.id = i.checkin_id " +
            "WHERE c.user_id = #{userId}")
    List<Map<String, Object>> selectImagePathsByUser(@Param("userId") Long userId);

    /** 删除本人打卡图片明细（先删子表，再删 meal_checkin 主表） */
    @Select("DELETE FROM meal_checkin_image " +
            "WHERE checkin_id IN (SELECT id FROM meal_checkin WHERE user_id = #{userId})")
    int deleteImagesByUser(@Param("userId") Long userId);

    /**
     * 上传清理任务用：一次性取出全部图片相对路径（原图 + 缩略图），
     * 与磁盘文件比对时放进 Set，避免逐文件查库（见 UploadCleanupJob）。
     */
    @Select("SELECT url FROM meal_checkin_image UNION SELECT thumb_url FROM meal_checkin_image")
    List<String> selectAllImagePaths();
}
