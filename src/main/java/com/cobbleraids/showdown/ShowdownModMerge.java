package com.cobbleraids.showdown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Merges one of CobbleRaids' Showdown data files into the matching file of Cobblemon's own format mod
 * ({@code data/mods/cobblemon/<name>.js}) instead of copying over it.
 *
 * <p>Showdown reads exactly one {@code conditions.js} (and one {@code items.js}) per format mod, and
 * Cobblemon keeps its own entries there: {@code alphaboost}, which the Wild Alpha rule adds to every
 * wild alpha. Until 2026-11 the installer copied our file over Cobblemon's, so alphas lost their boost
 * in every battle on the server. Our file is now installed beside Cobblemon's under its own name, and
 * a short hook appended to Cobblemon's file merges our export into it.
 *
 * <p>Cobblemon only re-unbundles Showdown when its Showdown version changes, so a server that ran the
 * old install still has our copy where Cobblemon's belongs. That copy is recognised by its signature
 * and replaced with Cobblemon's original, read from the bundled {@code data/cobblemon/showdown.zip}.
 *
 * <p>Pure: no Minecraft, no files. {@link ShowdownIntegrationInstaller} does the reading and writing.
 */
final class ShowdownModMerge {

    /** First line of every hook; everything from it to the end of the file is ours. */
    static final String HOOK_MARKER = "// cobbleraids:merge-hook";

    /**
     * One merged file.
     *
     * @param ownFile         our file's name beside Cobblemon's, e.g. {@code cobbleraids-conditions.js}
     * @param exportName      the table both files export, e.g. {@code Conditions}
     * @param legacySignature text only our own file contains, which marks the old install's copy
     */
    record Target(String ownFile, String exportName, String legacySignature) {}

    private ShowdownModMerge() {}

    /**
     * The file to write where Cobblemon's lives.
     *
     * @param onDisk   what is there now, or null
     * @param bundled  Cobblemon's original from its bundled zip, or null when it ships none
     * @param template the hook, with {@code __FILE__} and {@code __EXPORT__} placeholders
     */
    static String compose(String onDisk, String bundled, Target target, String template) {
        String existing = onDisk == null ? null : withoutHook(onDisk);
        String base;
        if (existing != null && !existing.isBlank() && !existing.contains(target.legacySignature())) {
            base = existing; // Cobblemon's, possibly with another mod's edits: keep it
        } else if (bundled != null && !bundled.isBlank()) {
            base = bundled;
        } else {
            base = "\"use strict\";\nmodule.exports = {" + target.exportName() + ": {}};\n";
        }
        String hook = template.replace("__FILE__", target.ownFile()).replace("__EXPORT__", target.exportName());
        return base.stripTrailing() + "\n\n" + hook.strip() + "\n";
    }

    private static String withoutHook(String source) {
        int at = source.indexOf(HOOK_MARKER);
        return at < 0 ? source : source.substring(0, at);
    }

    /** One entry of a zip as UTF-8 text, or null when the zip or the entry is missing. */
    static String bundledEntry(InputStream zip, String entryName) throws IOException {
        if (zip == null) return null;
        try (ZipInputStream in = new ZipInputStream(zip)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (!entry.getName().equals(entryName)) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                return out.toString(StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
