package com.couplemeal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 情侣带饭点餐小程序 · 后端单体启动类。
 * 技术栈：Spring Boot 3.4 + MyBatis-Plus + MySQL 8。
 */
@SpringBootApplication
@MapperScan("com.couplemeal.mapper")
public class CoupleMealApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoupleMealApplication.class, args);
    }
}
