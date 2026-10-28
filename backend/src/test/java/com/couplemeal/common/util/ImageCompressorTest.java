package com.couplemeal.common.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 头像重编码自测（无 DB）：验证长边 ≤256、输出 ≤100KB，且重编码后可读回为合法图片。
 * 见 10-关系绑定/后端.md §3.6（剥 EXIF 由重编码天然保证，此处断言尺寸/体积约束）。
 */
class ImageCompressorTest {

    private static byte[] makePng(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void compressAvatarRespectsEdgeAndSize() throws IOException {
        // 一张 2000x1500 的 PNG，原始体积远超限制
        byte[] png = makePng(2000, 1500);
        byte[] out = ImageCompressor.compressAvatar(
                new ByteArrayInputStream(png), 256, 102_400L);

        assertTrue(out.length <= 102_400L, "输出必须 ≤100KB，实际 " + out.length);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(out));
        assertTrue(result != null, "重编码结果必须是可解析的图片");
        int longest = Math.max(result.getWidth(), result.getHeight());
        assertTrue(longest <= 256, "长边必须 ≤256，实际 " + longest);
    }

    @Test
    void compressAvatarSmallImageKeepsEdge() throws IOException {
        byte[] png = makePng(100, 80);
        byte[] out = ImageCompressor.compressAvatar(
                new ByteArrayInputStream(png), 256, 102_400L);
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(out));
        int longest = Math.max(result.getWidth(), result.getHeight());
        assertTrue(longest <= 100, "小图长边不应被放大，实际 " + longest);
        assertTrue(out.length <= 102_400L, "输出必须 ≤100KB，实际 " + out.length);
    }
}
