package net.snobblemon.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The SnobbleCrates loot box's own pixel pictures (snobblecrates/crate.html: ART, pickArt(), art()): small letter grids coloured from the
 * reward's rarity colour and outlined automatically. Rendered here on the server and sent to the client like any other picture, so the
 * native screen shows exactly the drawings the web page made. Key: "x:<art>:<rrggbb>:<ball tint>".
 */
final class CrateArt {
    private CrateArt() {}

    static final Map<String, String[]> ART = new HashMap<>();
    static {
        ART.put("ball", new String[]{"..rrrrrr..", ".rlrrrrrr.", "rlrrrrrrrr", "rrrrrrrrrr", "kkkkwwkkkk", "kkkkwwkkkk", "wwwwwwwwww", "wwwwwwwwgg", ".wwwwwwgg.", "..wwwwww.."});
        ART.put("candy", new String[]{".p........p.", "ppp.aaaa.ppp", "pp.alaaaa.pp", "pp.aaaaaa.pp", "pp.aaaaad.pp", "ppp.dddd.ppp", ".p........p."});
        ART.put("gem", new String[]{"..hhhhhh..", ".haaaaaad.", "haaaaaaaad", ".aaaaaaad.", "..aaaaad..", "...aaad...", "....ad...."});
        ART.put("coin", new String[]{"..yyyyyy..", ".yyhhyyyY.", "yyhyyyyyyY", "yyhyYYyyyY", "yyyyYYyyyY", "yyyyyyyyyY", ".yyyyyyyY.", "..YYYYYY.."});
        ART.put("key", new String[]{".yyy........", "yy.yy.......", "yy.yyyyyyyyy", "yy.yy..y.y.y", ".yyy...y.y.."});
        ART.put("star", new String[]{".....yy.....", ".....yy.....", "....yyyy....", "yyyyyyyyyyyy", ".yyyyyyyyyy.", "..yyyyyyyy..", "..yyyyyyyy..", ".yyyy..yyyy.", ".yyy....yyy.", "yy........yy"});
        ART.put("crown", new String[]{"y....y....y", "yy..yyy..yy", "yyyyyyyyyyy", "yyryyyyyryy", "yyyyyyyyyyy", "YYYYYYYYYYY"});
        ART.put("ticket", new String[]{".yyyyyyyyyy.", "yyyYyyyyyyyy", "yyyYyyhhhyyy", "yyyYyyyyyyyy", ".yyyyyyyyyy."});
        ART.put("sword", new String[]{"..........ww", ".........wwg", "........wwg.", ".......wwg..", "..y...wwg...", "...y.wwg....", "....ywg.....", ".....yy.....", "....nyy.....", "...nn......."});
        ART.put("shield", new String[]{"aaaaaaaaaa", "alaaaaaaad", "alaaaaaaad", "alaaaaaaad", ".aaaaaaad.", ".aaaaaaad.", "..aaaaad..", "...aaad...", "....dd...."});
        ART.put("potion", new String[]{"...gg...", "...gg...", "..gggg..", "..gggg..", ".aaaaaa.", "aaalaaaa", "aaalaaaa", "aaaaaaad", ".aaaaad."});
        ART.put("egg", new String[]{"...aaaa...", "..aaaaaa..", ".aalaaaaa.", ".aalaaaaa.", "aaaaaddaaa", "aaaaaddaaa", "aaaaaaaaaa", ".aaaaaaaa.", "..aaaaaa.."});
        ART.put("book", new String[]{"nnnnnnnnnn", "naaaaaaaan", "naawwwwaan", "naaaaaaaan", "naawwwwaan", "naaaaaaaan", "nnnnnnnnnn"});
        ART.put("box", new String[]{"aaaaaaaaaa", "alllllllld", "alllllllld", "dddyyddddd", "aaayyaaaaa", "aaaaaaaaaa", "aaaaaaaaad", "dddddddddd"});
        ART.put("heart", new String[]{".rr..rr.", "rrrrrrrr", "rhrrrrrr", "rrrrrrrr", ".rrrrrr.", "..rrrr..", "...rr..."});
        ART.put("skull", new String[]{".wwwwww.", "wwwwwwww", "wkkwwkkw", "wkkwwkkw", "wwwkkwww", ".wwwwww.", ".wkwkwk."});
        ART.put("bolt", new String[]{"...yyy.", "..yyy..", ".yyy...", "yyyyyy.", "..yyy..", ".yy....", "yy....."});
    }
    private static final Object[][] KEYWORDS = {
            {"ball", "ball|pokeball|poke_ball|sphere"}, {"candy", "candy|sweet|lollipop"}, {"egg", "egg"}, {"key", "key|tripwire|hook"},
            {"ticket", "ticket|voucher|coupon|paper|pass"}, {"crown", "crown|rank|vip|tiara"}, {"sword", "sword|axe|trident|blade|bow|dagger|weapon"},
            {"shield", "shield|helmet|chestplate|leggings|boots|armor|armour"}, {"potion", "potion|bottle|elixir|bucket|vial"},
            {"book", "book|scroll|map|tome"}, {"heart", "heart|apple|health|revive|heal"}, {"bolt", "lightning|bolt|dust|blaze|redstone|glowstone|energy|boost"},
            {"coin", "coin|ingot|nugget|gold|money|cash|token|currency|\\$"}, {"gem", "diamond|emerald|amethyst|gem|crystal|shard|quartz|prism|jewel|ruby|sapphire"},
            {"skull", "skull|head$"}, {"star", "star|perk|bonus|prize"}};
    private static final Pattern[] KW = new Pattern[KEYWORDS.length];
    static { for (int i = 0; i < KEYWORDS.length; i++) KW[i] = Pattern.compile((String) KEYWORDS[i][1]); }

    private static String strip(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceFirst("^[a-z0-9_.-]+:", ""); }

    /** pickArt(rw) */
    static String pick(String icon, String item, String name, String kind) {
        String hint = strip(icon);
        if (ART.containsKey(hint)) return hint;
        String s = hint.isEmpty() ? strip(item) : hint;
        for (int i = 0; i < KW.length; i++) if (KW[i].matcher(s).find()) return (String) KEYWORDS[i][0];
        if (hint.isEmpty()) { String nm = name == null ? "" : name.toLowerCase(Locale.ROOT); for (int i = 0; i < KW.length; i++) if (KW[i].matcher(nm).find()) return (String) KEYWORDS[i][0]; }
        return "perk".equals(kind) ? "star" : "box";
    }

    /** The ball tint art() picks from the item id. */
    static String ballTint(String item) {
        String it = item == null ? "" : item;
        if (it.contains("great")) return "great";
        if (it.contains("ultra")) return "ultra";
        if (it.contains("master")) return "master";
        if (it.contains("premier")) return "premier";
        if (it.contains("beast")) return "beast";
        if (it.contains("luxury")) return "luxury";
        return "poke";
    }

    private static double[] mix(double[] a, double[] b, double t) { return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }

    /** art(name, rgb, item) as a PNG: the grid plus a one-pixel #0e0b18 outline. */
    static byte[] png(String name, int rgb, String tint) {
        String[] grid = ART.getOrDefault(name, ART.get("box"));
        int gw = 0;
        for (String r : grid) gw = Math.max(gw, r.length());
        int gh = grid.length, w2 = gw + 2, h2 = gh + 2;
        double[] a = {rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255};
        double[] ballRed = switch (tint) {
            case "great" -> new double[]{52, 110, 220}; case "ultra" -> new double[]{240, 200, 40}; case "master" -> new double[]{150, 70, 210};
            case "premier" -> new double[]{240, 240, 240}; case "beast" -> new double[]{40, 90, 200}; case "luxury" -> new double[]{40, 40, 40};
            default -> new double[]{214, 58, 58}; };
        Map<Character, double[]> pal = new HashMap<>();
        pal.put('a', a); pal.put('d', mix(a, new double[]{0, 0, 0}, .38)); pal.put('l', mix(a, new double[]{255, 255, 255}, .45)); pal.put('h', mix(a, new double[]{255, 255, 255}, .6));
        pal.put('r', ballRed); pal.put('y', new double[]{244, 200, 52}); pal.put('Y', new double[]{176, 130, 24}); pal.put('b', new double[]{60, 120, 230});
        pal.put('k', new double[]{34, 30, 38}); pal.put('w', new double[]{240, 240, 244}); pal.put('g', new double[]{150, 156, 170}); pal.put('n', new double[]{140, 92, 54});
        pal.put('p', new double[]{255, 140, 190}); pal.put('c', new double[]{80, 220, 230}); pal.put('v', new double[]{160, 90, 230});
        if (name.equals("ball")) pal.put('l', mix(ballRed, new double[]{255, 255, 255}, .5));
        if (name.matches("coin|crown|key|star|bolt|ticket")) pal.put('h', new double[]{255, 244, 170});
        boolean[][] filled = new boolean[h2][w2];
        for (int y = 0; y < h2; y++) for (int x = 0; x < w2; x++) {
            int gy = y - 1, gx = x - 1;
            char ch = gy >= 0 && gy < gh && gx >= 0 && gx < grid[gy].length() ? grid[gy].charAt(gx) : '.';
            filled[y][x] = ch != '.' && pal.containsKey(ch);
        }
        BufferedImage img = new BufferedImage(w2, h2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h2; y++) for (int x = 0; x < w2; x++) {
            if (filled[y][x]) {
                double[] c = pal.get(grid[y - 1].charAt(x - 1));
                img.setRGB(x, y, 0xFF000000 | (int) Math.max(0, Math.min(255, Math.round(c[0]))) << 16 | (int) Math.max(0, Math.min(255, Math.round(c[1]))) << 8 | (int) Math.max(0, Math.min(255, Math.round(c[2]))));
                continue;
            }
            boolean near = false;
            for (int dy = -1; dy <= 1 && !near; dy++) for (int dx = -1; dx <= 1; dx++) {
                int yy = y + dy, xx = x + dx;
                if ((dx != 0 || dy != 0) && yy >= 0 && yy < h2 && xx >= 0 && xx < w2 && filled[yy][xx]) { near = true; break; }
            }
            if (near) img.setRGB(x, y, 0xFF0E0B18);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            return new byte[0];
        }
    }

    /** "x:<art>:<rrggbb>:<tint>" -> PNG, or empty when malformed. */
    static byte[] fromKey(String key) {
        String[] p = key.split(":");
        if (p.length != 4 || !ART.containsKey(p[1]) || !p[2].matches("[0-9a-f]{6}") || !p[3].matches("[a-z]{1,8}")) return new byte[0];
        return png(p[1], Integer.parseInt(p[2], 16), p[3]);
    }

    static String key(String art, int rgb, String tint) { return "x:" + art + ":" + String.format("%06x", rgb & 0xFFFFFF) + ":" + tint; }

    /** The image's size in pixels (grid + outline), so the screen can draw it at a whole multiple like the page did. */
    static int[] size(String art) {
        String[] grid = ART.getOrDefault(art, ART.get("box"));
        int gw = 0;
        for (String r : grid) gw = Math.max(gw, r.length());
        return new int[]{gw + 2, grid.length + 2};
    }
}
