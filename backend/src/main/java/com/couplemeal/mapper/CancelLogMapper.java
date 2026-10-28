package com.couplemeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couplemeal.entity.CancelLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 取消日志 Mapper（cancel_log）。
 */
@Mapper
public interface CancelLogMapper extends BaseMapper<CancelLog> {
}
