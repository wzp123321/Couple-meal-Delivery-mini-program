package com.couplemeal.common.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片重编码工具：把任意受支持格式重编码为 JPEG（长边压到 maxEdge、质量 0.8 起步，
 * 循环降质直到 ≤ maxBytes）。重编码天然剥离 EXIF（不复制原图元数据）。
 *
 * <p>头像落盘统一为 JPEG，与 后端.md §3.6 一致（类型白名单 jpg/png/webp，输出 .jpg）。
 */
public final class ImageCompressor {

    private ImageCompressor() {
    }

    /**
     * 压缩头像为 JPEG 字节。
     *
     * @param in       原始图片输入流
     * @param maxEdge  长边上限（如 256）
     * @param maxBytes 输出字节上限（如 100KB）
     * @return 重编码后的 JPEG 字节
     */
    public static byte[] compressAvatar(InputStream in, int maxEdge, long maxBytes) throws IOException {
        BufferedImage src = ImageIO.read(in);
        if (src == null) {
            throw new IOException("无法解析图片");
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest > maxEdge && longest > 0) {
            double scale = (double) maxEdge / longest;
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            src = scaled;
        }

        float quality = 0.8f;
        byte[] out;
        while (true) {
            out = writeJpeg(src, quality);
            if (out.length <= maxBytes || quality <= 0.2f) {
                break;
            }
            quality -= 0.1f;
        }
        return out;
    }

    private static byte[] writeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
