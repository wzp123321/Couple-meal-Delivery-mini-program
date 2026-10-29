package com.couplemeal.job;

import com.couplemeal.config.StorageProperties;
import com.couplemeal.mapper.MealCheckinImageMapper;
import com.couplemeal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 上传目录孤儿文件清理（见 60-打卡 §上传 / D12）。
 *
 * <p>上传接口在上传成功时就落盘（打卡图两档 + 头像），但用户可能上传后不提交打卡、
 * 或换图后旧文件不再被引用；这些文件永远没人删，长期会把磁盘撑爆。
 *
 * <p>清理范围只有两类（与落盘规则一致）：
 * <ul>
 *   <li>{@code base-dir/yyyyMM/*.jpg} —— 打卡原图与缩略图，库内相对路径 {@code /upload/yyyyMM/x.jpg}</li>
 *   <li>{@code base-dir/avatar/*.jpg} —— 头像，库内相对路径 {@code /upload/avatar/x.jpg}</li>
 * </ul>
 *
 * <p>三个删除条件必须同时满足，缺一不可：
 * <ol>
 *   <li>文件 mtime 早于「现在 - 24h」（给「已上传但还没提交打卡」留窗口）</li>
 *   <li>相对路径不在 {@code meal_checkin_image.url / thumb_url} 中</li>
 *   <li>相对路径不在 {@code user.avatar} 中</li>
 * </ol>
 * 引用集合每轮一次性查库（两列 + 头像共 3 条查询），绝不逐文件查库。
 *
 * <p>安全护栏：base-dir 不存在只打 WARN 跳过；引用集合查库失败直接放弃本轮（拿不到引用就不敢删）；
 * 单文件删除失败只记 WARN，继续处理其余文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadCleanupJob {

    /** 库内相对路径的统一前缀，与 {@link StorageProperties#resolveAbsolute(String)} 互为逆运算 */
    private static final String UPLOAD_PREFIX = "/upload";

    /** 递归层数：base-dir/&lt;子目录&gt;/&lt;文件&gt; 即 2 层 */
    private static final int MAX_DEPTH = 2;

    /** mtime 早于「现在 - 该小时数」才允许删除 */
    private static final long RETENTION_HOURS = 24L;

    /** 头像子目录名 */
    private static final String AVATAR_DIR = "avatar";

    /** 打卡图按月目录名 yyyyMM */
    private static final String MONTH_DIR_REGEX = "\\d{6}";

    private final StorageProperties storageProperties;
    private final MealCheckinImageMapper mealCheckinImageMapper;
    private final UserMapper userMapper;

    @Scheduled(cron = "${couple-meal.schedule.upload-cleanup-cron:0 40 3 * * *}")
    public void run() {
        Path base;
        try {
            base = resolveBaseDir();
        } catch (RuntimeException e) {
            log.error("UploadCleanupJob base-dir 非法，跳过本轮：{}", storageProperties.getBaseDir(), e);
            return;
        }
        if (!Files.isDirectory(base)) {
            log.warn("UploadCleanupJob base-dir 不存在，跳过本轮：{}", base);
            return;
        }

        Set<String> referenced;
        try {
            referenced = loadReferencedPaths();
        } catch (Exception e) {
            // 拿不到引用集合就删文件 = 盲删，宁可本轮不清理
            log.error("UploadCleanupJob 读取引用路径失败，跳过本轮：{}", base, e);
            return;
        }

        Instant deadline = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS);
        int scanned = 0;
        int deleted = 0;
        int kept = 0;
        try (Stream<Path> stream = Files.walk(base, MAX_DEPTH)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                if (!isCleanupCandidate(base, file)) {
                    continue;
                }
                scanned++;
                try {
                    if (!Files.getLastModifiedTime(file).toInstant().isBefore(deadline)) {
                        kept++;
                        continue;
                    }
                    if (referenced.contains(toRelative(file))) {
                        kept++;
                        continue;
                    }
                    Files.deleteIfExists(file);
                    deleted++;
                } catch (IOException e) {
                    // 单文件失败不影响其余
                    kept++;
                    log.warn("UploadCleanupJob 删除失败 file={}", file, e);
                }
            }
        } catch (IOException e) {
            log.error("UploadCleanupJob 扫描异常 base={}", base, e);
        }
        log.info("UploadCleanupJob 扫描 {} / 删除 {} / 保留 {}", scanned, deleted, kept);
    }

    /** 只处理 base-dir/{avatar|yyyyMM}/*.jpg 两类文件，其余（未知目录、非 jpg）一律不碰 */
    private boolean isCleanupCandidate(Path base, Path file) {
        Path parent = file.getParent();
        if (parent == null || parent.equals(base)) {
            return false;
        }
        String subDir = parent.getFileName().toString();
        if (!AVATAR_DIR.equals(subDir) && !subDir.matches(MONTH_DIR_REGEX)) {
            return false;
        }
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jpg");
    }

    /** 一次性取出全部被 DB 引用的相对路径：打卡原图 + 缩略图 + 头像 */
    private Set<String> loadReferencedPaths() {
        Set<String> referenced = new HashSet<>();
        for (String path : mealCheckinImageMapper.selectAllImagePaths()) {
            addIfNotBlank(referenced, path);
        }
        for (String avatar : userMapper.selectAllAvatars()) {
            addIfNotBlank(referenced, avatar);
        }
        return referenced;
    }

    private void addIfNotBlank(Set<String> target, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String p = path.trim();
        // 统一成「/upload 起的相对路径」再比较：历史数据里可能存过完整 URL（https://域名/upload/x.jpg），
        // 若直接按原样入集合，会与 toRelative() 产出的 "/upload/x.jpg" 永不相等，导致把所有头像当孤儿删掉。
        int idx = p.indexOf(UPLOAD_PREFIX);
        if (idx > 0) {
            p = p.substring(idx);
        }
        target.add(p);
    }

    /**
     * 磁盘绝对路径 → 库内相对路径，是 {@link StorageProperties#resolveAbsolute(String)} 的逆运算：
     * {@code baseDir/yyyyMM/x.jpg → /upload/yyyyMM/x.jpg}。
     * 全类唯一的换算入口，禁止在别处手工拼 base-dir 或 "/upload"。
     */
    private String toRelative(Path absoluteFile) {
        Path base = resolveBaseDir();
        String rel = base.relativize(absoluteFile.toAbsolutePath().normalize()).toString()
                .replace('\\', '/');
        return UPLOAD_PREFIX + "/" + rel;
    }

    private Path resolveBaseDir() {
        return Paths.get(storageProperties.getBaseDir()).toAbsolutePath().normalize();
    }
}
