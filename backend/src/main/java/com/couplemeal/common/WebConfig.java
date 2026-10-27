package com.couplemeal.common;

import com.couplemeal.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册鉴权拦截器 + 静态资源（上传图片）映射。
 *
 * <p>拦截范围：所有 /api 路径（context-path 已为 /api），排除：
 *   - /auth/login        微信登录免 token
 *   - /wx/media-check-callback  微信审核回调免 token
 *   - /upload/**         静态图片（见 D12）
 * 其余业务接口强制携带有效 token。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final StorageProperties storageProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/wx/media-check-callback",
                        "/upload/**",
                        "/error");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传目录静态映射：/upload/** -> 磁盘 base-dir（见 D12）
        // 注意：base-dir 末尾不带斜杠，映射前缀带斜杠，二者拼接为文件系统绝对路径。
        String baseDir = storageProperties.getBaseDir();
        if (!baseDir.endsWith("/")) {
            baseDir = baseDir + "/";
        }
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + baseDir);
    }
}
