package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知 Mapper（notification）。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
