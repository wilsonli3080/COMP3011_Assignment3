package comp3011;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.awt.font.GlyphVector;

import org.bytedeco.javacv.Frame;

public class FrameSubtitleProcessor extends FrameProcessor {
    private static final int MAX_LINE_LENGTH = 42;
    private volatile List<SubtitleSegment> segments = List.of();
    private volatile boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSegments(List<SubtitleSegment> segments) {
        this.segments = List.copyOf(segments);
    }

    public void clear() {
        segments = List.of();
    }

    @Override
    public void process(Frame frame, InfoFrame info) throws Exception {
        if (!enabled || frame.image == null || frame.image.length == 0
                || !(frame.image[0] instanceof ByteBuffer pixels)) {
            return;
        }
        if (videoInfo.imageDepth() != Frame.DEPTH_UBYTE && videoInfo.imageDepth() != Frame.DEPTH_BYTE) {
            throw new Exception("FrameSubtitleProcessor requires 8-bit image frames");
        }
        if (videoInfo.imageChannels() < 3) {
            throw new Exception("FrameSubtitleProcessor requires at least three image channels");
        }

        double timestamp = info.timestampUs() / 1_000_000.0;
        SubtitleSegment active = findActiveSegment(timestamp);
        if (active == null) {
            return;
        }

        List<String> lines = wrap(active.text());
        if (lines.isEmpty()) {
            return;
        }

        int width = videoInfo.imageWidth();
        int height = videoInfo.imageHeight();
        int channels = videoInfo.imageChannels();
        int stride = videoInfo.imageStride();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] imageBytes = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        ByteBuffer source = pixels.duplicate();
        for (int y = 0; y < height; y++) {
            int sourceRow = y * stride;
            int targetRow = y * width * 3;
            for (int x = 0; x < width; x++) {
                int sourcePixel = sourceRow + x * channels;
                int targetPixel = targetRow + x * 3;
                imageBytes[targetPixel] = source.get(sourcePixel);
                imageBytes[targetPixel + 1] = source.get(sourcePixel + 1);
                imageBytes[targetPixel + 2] = source.get(sourcePixel + 2);
            }
        }

        drawSubtitle(image, lines);
        for (int y = 0; y < height; y++) {
            int sourceRow = y * width * 3;
            int targetRow = y * stride;
            for (int x = 0; x < width; x++) {
                int sourcePixel = sourceRow + x * 3;
                int targetPixel = targetRow + x * channels;
                pixels.put(targetPixel, imageBytes[sourcePixel]);
                pixels.put(targetPixel + 1, imageBytes[sourcePixel + 1]);
                pixels.put(targetPixel + 2, imageBytes[sourcePixel + 2]);
            }
        }
    }

    private SubtitleSegment findActiveSegment(double timestamp) {
        for (SubtitleSegment segment : segments) {
            double displayEnd = Math.min(Math.max(segment.end(), segment.start() + 1.0), segment.start() + 7.0);
            if (timestamp >= segment.start() && timestamp <= displayEnd) {
                return segment;
            }
        }
        return null;
    }

    private void drawSubtitle(BufferedImage image, List<String> lines) {
        Graphics2D graphics = image.createGraphics();
        try {
            int fontSize = Math.max(16, image.getHeight() / 22);
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
            graphics.setFont(font);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            FontRenderContext context = graphics.getFontRenderContext();
            int lineHeight = (int) Math.ceil(font.getLineMetrics("Ag", context).getHeight());
            int bottomMargin = Math.max(12, image.getHeight() / 30);
            int baseline = image.getHeight() - bottomMargin - (lines.size() - 1) * lineHeight;
            float outlineWidth = Math.max(2.0f, fontSize / 12.0f);

            for (String line : lines) {
                int textWidth = (int) Math.ceil(font.getStringBounds(line, context).getWidth());
                int x = Math.max(2, (image.getWidth() - textWidth) / 2);
                graphics.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(Color.BLACK);
                GlyphVector glyphs = font.createGlyphVector(context, line);
                Shape outline = glyphs.getOutline(x, baseline);
                graphics.draw(outline);
                graphics.setColor(Color.WHITE);
                graphics.fill(outline);
                baseline -= lineHeight;
            }
        } finally {
            graphics.dispose();
        }
    }

    private static List<String> wrap(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(2);
        String remaining = normalized;
        while (!remaining.isEmpty() && lines.size() < 2) {
            if (remaining.length() <= MAX_LINE_LENGTH) {
                lines.add(remaining);
                remaining = "";
                break;
            }

            int split = remaining.lastIndexOf(' ', MAX_LINE_LENGTH);
            int punctuation = lastPunctuationBoundary(remaining, MAX_LINE_LENGTH);
            if (punctuation > 0) {
                int afterPunctuation = remaining.indexOf(' ', punctuation);
                if (afterPunctuation > 0 && afterPunctuation <= MAX_LINE_LENGTH) {
                    split = afterPunctuation;
                }
            }
            if (split <= 0) {
                split = MAX_LINE_LENGTH;
            }
            lines.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }

        if (!remaining.isEmpty() && !lines.isEmpty()) {
            String finalLine = lines.get(lines.size() - 1);
            int limit = MAX_LINE_LENGTH - 1;
            if (finalLine.length() > limit) {
                finalLine = finalLine.substring(0, limit);
            }
            lines.set(lines.size() - 1, finalLine + "\u2026");
        }
        return lines;
    }

    private static int lastPunctuationBoundary(String text, int limit) {
        for (int i = Math.min(limit - 1, text.length() - 1); i >= 0; i--) {
            if (".!?;,:".indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }
}
