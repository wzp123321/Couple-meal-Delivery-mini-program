package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.MenuItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜单与忌口库 Mapper（menu_item）。
 * 注意：业务查询必须带 status = 1（MenuStatus.NORMAL），否则软删的菜会重新出现。
 */
@Mapper
public interface MenuItemMapper extends BaseMapper<MenuItem> {
}
