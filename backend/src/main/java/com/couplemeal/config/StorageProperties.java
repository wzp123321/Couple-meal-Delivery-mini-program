package com.couplemeal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上传/存储配置（见 00-全局约定.md §5 + D12）。
 * 库内只存相对路径 /upload/yyyyMM/x.jpg，由本配置给出磁盘根目录与 URL 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "couple-meal.upload")
public class StorageProperties {

    /** 磁盘根目录，如 /data/upload */
    private String baseDir = "/data/upload";

    /** 对外 URL 前缀，如 https://域名/upload */
    private String urlPrefix = "https://your-domain/upload";

    private long maxRawSize = 10_485_760L;
    private long maxOutputSize = 512_000L;
    private int maxEdge = 1080;
    private int thumbEdge = 320;
    private long thumbMaxSize = 61_440L;

    private long avatarMaxRawSize = 2_097_152L;
    private int avatarEdge = 256;
    private long avatarMaxSize = 102_400L;

    /**
     * 将库内相对路径（形如 /upload/avatar/xxx.jpg）解析为磁盘绝对路径。
     * 规则：baseDir + 去掉 "/upload" 前缀后的剩余部分。
     * 落盘/删除文件时统一走本方法，避免路径拼接错误。
     */
    public String resolveAbsolute(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        String base = baseDir.endsWith("/") ? baseDir.substring(0, baseDir.length() - 1) : baseDir;
        if (relativePath.startsWith("/upload")) {
            return base + relativePath.substring("/upload".length());
        }
        return base + relativePath;
    }
}
