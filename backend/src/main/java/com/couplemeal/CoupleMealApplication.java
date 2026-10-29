package com.couplemeal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * 情侣带饭点餐小程序 · 后端单体启动类。
 * 技术栈：Spring Boot 3.4 + MyBatis-Plus + MySQL 8。
 *
 * <p>时区：启动即固定 JVM 默认时区为 {@link #DEFAULT_TIME_ZONE}。
 * 全后端用 {@code LocalDate.now() / LocalDateTime.now()} 判定发起截止（午饭 11:00 / 晚饭 18:00）、
 * 次日 02:30 过期归档、24h 自动完成与工作日判断，这些语义都按北京时间；
 * 若不固定，部署到 UTC 主机时全部偏移 8 小时，且不会报错、只表现为「截止时间不对」。
 * 必须在 {@code SpringApplication.run} 之前设置，确保任何 Bean 初始化前生效。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.couplemeal.mapper")
public class CoupleMealApplication {

    /** 业务时区，与 JDBC serverTimezone、前端设备时间保持一致 */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
        SpringApplication.run(CoupleMealApplication.class, args);
    }
}
