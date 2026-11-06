package net.snobblemon.server;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The CobbleRaids trophy case's tier glow (trophy.css .stagebox):
 *   radial-gradient(circle at 50% 75%, color-mix(in srgb, &lt;tier colour&gt; 38%, transparent), transparent 70%)
 * rendered as a picture the client stretches over the case (key "g:&lt;rrggbb&gt;"). A circle gradient's size is the farthest corner.
 */
final class GlowArt {
    private GlowArt() {}

    private static final int W = 216, H = 180;      // half the case's size (432 x 361 page units); drawn smoothly scaled

    static byte[] fromKey(String key) {
        String[] p = key.split(":");
        if (p.length != 2 || !p[1].matches("[0-9a-f]{6}")) return new byte[0];
        int rgb = Integer.parseInt(p[1], 16);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        double cx = W * 0.5, cy = H * 0.75;
        double r = Math.max(Math.hypot(cx, cy), Math.max(Math.hypot(W - cx, cy), Math.max(Math.hypot(cx, H - cy), Math.hypot(W - cx, H - cy))));
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double t = Math.min(1, Math.hypot(x + 0.5 - cx, y + 0.5 - cy) / (0.7 * r));
            int a = (int) Math.round(255 * 0.38 * (1 - t));
            if (a > 0) img.setRGB(x, y, a << 24 | rgb);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
