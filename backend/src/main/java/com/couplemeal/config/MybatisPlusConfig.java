package com.couplemeal.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.couplemeal.common.util.IdUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：
 *  1. 覆盖默认 64bit 雪花，改用项目 53bit 雪花（IdUtil），保证返回小程序不丢精度（D1/D13）。
 *  2. 分页插件（MySQL）+ 乐观锁插件（meal_plan.version 防并发改状态）。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 自定义主键生成器：强制走 IdUtil.nextId()（53bit）。
     * 实体统一 @TableId(type = IdType.ASSIGN_ID) 即触发此生成器。
     */
    @Bean
    public IdentifierGenerator identifierGenerator() {
        return entity -> IdUtil.nextId();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(
                com.baomidou.mybatisplus.annotation.DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
