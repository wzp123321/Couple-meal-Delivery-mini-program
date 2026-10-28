package com.couplemeal.common.util;

import com.couplemeal.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 头像文件存储（D16 / 后端.md §3.6）：把「相对路径怎么定、文件写哪、旧文件怎么删、怎么证明是本人在用」
 * 收敛到一处，业务层只看到相对路径字符串。
 *
 * <p>三条硬规则：
 *   1. 库内只存相对路径 {@value #PATH_PREFIX}{userId}_{yyyyMMddHHmmss}.jpg，不存完整 URL（D12）。
 *   2. 文件名必须带时间戳——固定名会让微信/浏览器强缓存，换头像后界面还是旧图。
 *   3. 文件名必须带 userId——这是「路径归属」的唯一凭据，防用户填他人头像路径盗图（4004）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarStorage {

    /** 头像相对路径前缀，与 Nginx 的 /upload 静态映射对齐 */
    public static final String PATH_PREFIX = "/upload/avatar/";

    /** 头像目录下的文件名必须形如 {userId}_{yyyyMMddHHmmss}.jpg，杜绝路径穿越与伪造文件名 */
    private static final Pattern FILE_NAME = Pattern.compile("^(\\d+)_(\\d{14})\\.jpg$");

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StorageProperties storageProperties;

    /**
     * 生成新的头像相对路径（带 userId 与当前时间戳）。只生成路径，不落盘。
     */
    public String buildRelativePath(Long userId) {
        return PATH_PREFIX + userId + "_" + LocalDateTime.now().format(TS_FMT) + ".jpg";
    }

    /**
     * 将已重编码、已过审的头像字节落盘，返回相对路径。目录不存在时自动创建。
     */
    public String save(Long userId, byte[] image) throws IOException {
        String relativePath = buildRelativePath(userId);
        Path abs = Paths.get(storageProperties.resolveAbsolute(relativePath));
        Files.createDirectories(abs.getParent());
        Files.write(abs, image);
        return relativePath;
    }

    /**
     * 删除一张旧头像。失败只记 WARN——文件删除是幂等的清理动作，不能因为 IO 失败回滚业务。
     */
    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || !relativePath.startsWith(PATH_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(storageProperties.resolveAbsolute(relativePath)));
        } catch (IOException e) {
            log.warn("删除头像文件失败 path={}", relativePath, e);
        }
    }

    /**
     * 判断该头像是不是「本服务为当前用户上传过的」：前缀正确 + 文件名规范且含本人 userId + 文件确实存在。
     * 三者缺一即视为无效（调用方返回 4004）。
     */
    public boolean belongsTo(String relativePath, Long userId) {
        if (relativePath == null || userId == null || !relativePath.startsWith(PATH_PREFIX)) {
            return false;
        }
        String fileName = relativePath.substring(PATH_PREFIX.length());
        var m = FILE_NAME.matcher(fileName);
        if (!m.matches() || !m.group(1).equals(String.valueOf(userId))) {
            return false;
        }
        return Files.exists(Paths.get(storageProperties.resolveAbsolute(relativePath)));
    }

    /** 相对路径转磁盘绝对路径（-upload 前缀由 baseDir 承接） */
    public String toAbsolute(String relativePath) {
        return storageProperties.resolveAbsolute(relativePath);
    }
}
