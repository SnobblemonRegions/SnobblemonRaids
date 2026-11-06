package net.snobblemon.server;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The wardrobe page's picture filters, applied on the server so the native screen shows the same pixels:
 *   .card .art img        filter: drop-shadow(0 6px 6px rgba(0,0,0,.45))                      (shown 168px wide)
 *   .card.locked .art img filter: grayscale(.85) brightness(.55) drop-shadow(...)
 * Key "w:<thumb name>:<0|1>" (1 = locked). The source is a thumbnail shipped in this mod (data/snobblemon_server/art).
 */
final class WardrobeArt {
    private WardrobeArt() {}

    private static final double SHOWN = 168.0;

    static byte[] fromKey(String key) {
        String[] p = key.split(":");
        if (p.length != 3 || !p[1].matches("[a-z0-9_]{1,44}") || !p[2].matches("[01]")) return new byte[0];
        try (InputStream in = WardrobeArt.class.getResourceAsStream("/data/snobblemon_server/art/" + p[1] + ".png")) {
            if (in == null) return new byte[0];
            BufferedImage src = ImageIO.read(in);
            if (src == null || src.getWidth() > 512 || src.getHeight() > 512) return new byte[0];
            for (int size = src.getWidth(); size >= 96; size = size * 3 / 4) {
                BufferedImage use = src;
                if (size != src.getWidth()) {
                    use = new BufferedImage(size, size * src.getHeight() / src.getWidth(), BufferedImage.TYPE_INT_ARGB);
                    var g = use.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(src, 0, 0, use.getWidth(), use.getHeight(), null);
                    g.dispose();
                }
                byte[] data = png(render(use, p[2].equals("1")));
                if (data.length <= 65536) return data;            // what one picture packet can carry
            }
            return new byte[0];
        } catch (IOException | RuntimeException e) {
            return new byte[0];
        }
    }

    static BufferedImage render(BufferedImage src, boolean locked) {
        int w = src.getWidth(), h = src.getHeight();
        double k = w / SHOWN;                                   // source pixels per CSS pixel
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        if (locked) {
            double s = 1 - 0.85, br = 0.55;
            for (int i = 0; i < px.length; i++) {
                int a = px[i] >>> 24, r = px[i] >> 16 & 255, g = px[i] >> 8 & 255, b = px[i] & 255;
                double r2 = (0.2126 + 0.7874 * s) * r + (0.7152 - 0.7152 * s) * g + (0.0722 - 0.0722 * s) * b;
                double g2 = (0.2126 - 0.2126 * s) * r + (0.7152 + 0.2848 * s) * g + (0.0722 - 0.0722 * s) * b;
                double b2 = (0.2126 - 0.2126 * s) * r + (0.7152 - 0.7152 * s) * g + (0.0722 + 0.9278 * s) * b;
                px[i] = a << 24 | clamp(r2 * br) << 16 | clamp(g2 * br) << 8 | clamp(b2 * br);
            }
        }
        // drop-shadow: the alpha, blurred (std deviation = blur radius / 2), moved down, at 45% black, under the picture
        double sigma = 3 * k;
        int dy = (int) Math.round(6 * k);
        float[] al = new float[w * h];
        for (int i = 0; i < px.length; i++) al[i] = (px[i] >>> 24) / 255f;
        int r = Math.max(1, (int) Math.round(Math.sqrt(12 * sigma * sigma / 3 + 1) / 2));   // three box passes ~ gaussian
        for (int pass = 0; pass < 3; pass++) { al = boxH(al, w, h, r); al = boxV(al, w, h, r); }
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int sy = y - dy;
            double sa = sy >= 0 && sy < h ? al[sy * w + x] * 0.45 : 0;
            int c = px[y * w + x];
            double fa = (c >>> 24) / 255.0;
            double oa = fa + sa * (1 - fa);
            if (oa <= 0) { out[y * w + x] = 0; continue; }
            double rr = ((c >> 16 & 255) * fa) / oa, gg = ((c >> 8 & 255) * fa) / oa, bb = ((c & 255) * fa) / oa;   // shadow is black
            out[y * w + x] = clamp(oa * 255) << 24 | clamp(rr) << 16 | clamp(gg) << 8 | clamp(bb);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        return img;
    }

    private static float[] boxH(float[] a, int w, int h, int r) {
        float[] o = new float[a.length];
        for (int y = 0; y < h; y++) {
            float sum = 0;
            int row = y * w;
            for (int x = -r; x <= r; x++) sum += x >= 0 && x < w ? a[row + x] : 0;
            for (int x = 0; x < w; x++) {
                o[row + x] = sum / (2 * r + 1);
                int add = x + r + 1, rem = x - r;
                if (add < w) sum += a[row + add];
                if (rem >= 0) sum -= a[row + rem];
            }
        }
        return o;
    }

    private static float[] boxV(float[] a, int w, int h, int r) {
        float[] o = new float[a.length];
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int y = -r; y <= r; y++) sum += y >= 0 && y < h ? a[y * w + x] : 0;
            for (int y = 0; y < h; y++) {
                o[y * w + x] = sum / (2 * r + 1);
                int add = y + r + 1, rem = y - r;
                if (add < h) sum += a[add * w + x];
                if (rem >= 0) sum -= a[rem * w + x];
            }
        }
        return o;
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
