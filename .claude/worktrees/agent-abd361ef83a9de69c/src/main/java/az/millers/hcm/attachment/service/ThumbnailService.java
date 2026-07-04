package az.millers.hcm.attachment.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generates a 256-px JPEG thumbnail from an image upload using only
 * {@code javax.imageio} (built into the JDK — no extra dependency).
 * Called by {@link AttachmentService#upload}.
 *
 * <p>Failure modes are deliberately swallowed: an unreadable image, an
 * unsupported color model, an OOM on a huge TIFF — the upload itself
 * already succeeded by the time {@link #generate(byte[], String)} runs,
 * and the {@code /thumbnail} endpoint falls back to the original blob
 * when no thumb exists. Returning {@code null} here is the documented
 * way to say "don't store a thumb for this row" (PRD 14.6 / 16.4 — M36).
 */
@Service
public class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);

    /**
     * Content types we know how to decode via {@code ImageIO}. Kept as
     * a static allowlist rather than relying on {@code ImageReader}
     * registry probes so a corrupt registry doesn't accidentally turn
     * on processing for SVG / TIFF — neither of which we want to
     * thumbnail (SVG is text + vector; TIFF can be enormous + slow).
     */
    private static final Set<String> THUMBNAILABLE = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif",
            "image/bmp", "image/webp");

    private final int maxEdgePx;
    private final float jpegQuality;

    public ThumbnailService(
            @Value("${hcm.attachment.thumbnail.max-edge-px:256}") int maxEdgePx,
            @Value("${hcm.attachment.thumbnail.jpeg-quality:0.85}") float jpegQuality) {
        this.maxEdgePx = maxEdgePx;
        this.jpegQuality = jpegQuality;
    }

    /** True when this content type is on our generation allowlist. */
    public boolean isThumbnailable(String contentType) {
        if (contentType == null) return false;
        // Strip any "; charset=…" suffix and lower-case.
        int semi = contentType.indexOf(';');
        String base = (semi < 0 ? contentType : contentType.substring(0, semi))
                .trim()
                .toLowerCase();
        return THUMBNAILABLE.contains(base);
    }

    /**
     * Decodes {@code source}, resizes to fit in a {@link #maxEdgePx}
     * square preserving aspect ratio, and encodes as JPEG at
     * {@link #jpegQuality}. Returns {@code null} when the content type
     * isn't thumbnailable, the bytes don't decode, or any other
     * generation error fires — see the class-level note on failure
     * handling.
     */
    public byte[] generate(byte[] source, String contentType) {
        if (!isThumbnailable(contentType)) return null;
        if (source == null || source.length == 0) return null;

        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(source));
        } catch (IOException e) {
            log.warn("Thumbnail decode failed (contentType={}): {}",
                    contentType, e.getMessage());
            return null;
        }
        if (src == null) {
            // ImageIO returns null when no reader could decode the
            // bytes — e.g. a JPEG signature with a corrupt body.
            log.warn("Thumbnail decode returned null (contentType={}, bytes={})",
                    contentType, source.length);
            return null;
        }

        int origW = src.getWidth();
        int origH = src.getHeight();
        if (origW <= 0 || origH <= 0) return null;

        // Fit-inside-square; preserve aspect ratio. Small images are
        // pass-through-sized (don't upscale a 96×96 favicon to 256).
        double scale = Math.min(
                1.0,
                (double) maxEdgePx / Math.max(origW, origH));
        int targetW = Math.max(1, (int) Math.round(origW * scale));
        int targetH = Math.max(1, (int) Math.round(origH * scale));

        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            // White background — JPEG can't represent transparency,
            // and the alpha channel of a transparent PNG would
            // otherwise render as black.
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, targetW, targetH);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }

        return encodeJpeg(dst);
    }

    private byte[] encodeJpeg(BufferedImage img) {
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(jpegQuality);

            ByteArrayOutputStream out = new ByteArrayOutputStream(8 * 1024);
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            log.warn("Thumbnail JPEG encode failed: {}", e.getMessage());
            return null;
        }
    }
}
