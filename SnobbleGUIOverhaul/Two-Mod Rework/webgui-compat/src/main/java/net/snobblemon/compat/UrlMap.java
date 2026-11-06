package net.snobblemon.compat;

import java.util.ArrayList;
import java.util.List;

/** Turns a WebGUI page url into the id of a native layout: {@code webgui://cobbleraids/hub/hub.html?x=1} becomes {@code cobbleraids_hub}. */
public final class UrlMap {
    private UrlMap() {}

    public static String pageId(String url) {
        if (url == null) return "";
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        if (u.startsWith("webgui://")) u = u.substring(9);
        else if (u.startsWith("webgui:/")) u = u.substring(8);
        List<String> parts = new ArrayList<>();
        for (String seg : u.split("/")) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) continue;
            if (seg.endsWith(".html")) seg = seg.substring(0, seg.length() - 5);
            seg = seg.toLowerCase().replaceAll("[^a-z0-9_-]", "");
            if (seg.isEmpty()) continue;
            if (!parts.isEmpty() && parts.get(parts.size() - 1).equals(seg)) continue;      // hub/hub -> hub
            parts.add(seg);
        }
        if (parts.size() > 1 && parts.get(parts.size() - 1).equals("index")) parts.remove(parts.size() - 1);
        return String.join("_", parts);
    }
}
