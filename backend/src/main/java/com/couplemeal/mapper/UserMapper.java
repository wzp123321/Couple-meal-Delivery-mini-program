package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper。仅提供 M0 登录所需的按 openid 查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT id, openid, nickname, avatar, create_time, update_time " +
            "FROM `user` WHERE openid = #{openid} LIMIT 1")
    User selectByOpenid(@Param("openid") String openid);

    /** 上传清理任务用：一次性取出全部头像相对路径（非空），避免逐文件查库 */
    @Select("SELECT avatar FROM `user` WHERE avatar IS NOT NULL AND avatar <> ''")
    List<String> selectAllAvatars();
}
