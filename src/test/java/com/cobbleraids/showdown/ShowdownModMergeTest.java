package com.cobbleraids.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CobbleRaids' Showdown data files are merged into Cobblemon's, never copied over them.
 *
 * <p>Copying over them is what happened from 2026-09 to 2026-11: our conditions.js replaced
 * Cobblemon 1.8.2's own data/mods/cobblemon/conditions.js, which holds {@code alphaboost}, and the
 * Wild Alpha rule's {@code addVolatile("alphaboost")} quietly did nothing in every battle on the
 * server. Cobblemon only re-unbundles when its Showdown version changes, so a server that ran the old
 * install still has our copy on disk: the original has to come back from Cobblemon's bundled zip.
 */
class ShowdownModMergeTest {

    private static final String TEMPLATE =
            "// cobbleraids:merge-hook __FILE__\n;(function(){ require('./__FILE__').__EXPORT__; })();\n";

    private static final String COBBLEMON_CONDITIONS =
            "\"use strict\";\nconst Conditions = {\n  alphaboost: {\n    name: \"alphaboost\"\n  }\n};\n"
            + "module.exports = __toCommonJS(conditions_exports);\n";

    /** What the old installer left on disk: our file alone. */
    private static final String LEGACY_OWN_COPY =
            "\"use strict\";\nconst Conditions = {};\nConditions.raidboss = {\n  name: 'raidboss'\n};\n"
            + "module.exports = {Conditions};\n";

    private static ShowdownModMerge.Target conditions() {
        return new ShowdownModMerge.Target("cobbleraids-conditions.js", "Conditions", "Conditions.raidboss =");
    }

    @Test
    @DisplayName("Cobblemon's own file is kept and our hook is appended to it")
    void keepsCobblemonsFile() {
        String merged = ShowdownModMerge.compose(COBBLEMON_CONDITIONS, COBBLEMON_CONDITIONS, conditions(), TEMPLATE);
        assertTrue(merged.startsWith(COBBLEMON_CONDITIONS.stripTrailing()), merged);
        assertTrue(merged.contains("alphaboost"));
        assertTrue(merged.contains("require('./cobbleraids-conditions.js').Conditions"), merged);
        assertEquals(1, count(merged, "cobbleraids:merge-hook"));
    }

    @Test
    @DisplayName("our old copy on disk is replaced by Cobblemon's original from the bundle")
    void repairsTheLegacyOverwrite() {
        String merged = ShowdownModMerge.compose(LEGACY_OWN_COPY, COBBLEMON_CONDITIONS, conditions(), TEMPLATE);
        assertTrue(merged.contains("alphaboost"), merged);
        assertFalse(merged.contains("Conditions.raidboss ="), "the legacy copy must not survive: " + merged);
        assertEquals(1, count(merged, "cobbleraids:merge-hook"));
    }

    @Test
    @DisplayName("running the install again changes nothing")
    void idempotent() {
        String once = ShowdownModMerge.compose(COBBLEMON_CONDITIONS, COBBLEMON_CONDITIONS, conditions(), TEMPLATE);
        assertEquals(once, ShowdownModMerge.compose(once, COBBLEMON_CONDITIONS, conditions(), TEMPLATE));
    }

    @Test
    @DisplayName("another mod's edits to Cobblemon's file on disk win over the bundled original")
    void respectsOtherModsEdits() {
        String edited = COBBLEMON_CONDITIONS + "// another mod was here\n";
        String merged = ShowdownModMerge.compose(edited, COBBLEMON_CONDITIONS, conditions(), TEMPLATE);
        assertTrue(merged.contains("// another mod was here"), merged);
    }

    @Test
    @DisplayName("with no file anywhere, a stub that exports an empty table carries the hook")
    void stubWhenNothingExists() {
        String merged = ShowdownModMerge.compose(null, null, conditions(), TEMPLATE);
        assertTrue(merged.contains("module.exports = {Conditions: {}};"), merged);
        assertTrue(merged.contains("cobbleraids:merge-hook cobbleraids-conditions.js"), merged);
    }

    @Test
    @DisplayName("the legacy copy with no bundled original falls back to the stub, not to our old copy")
    void legacyWithoutBundle() {
        String merged = ShowdownModMerge.compose(LEGACY_OWN_COPY, null, conditions(), TEMPLATE);
        assertFalse(merged.contains("Conditions.raidboss ="), merged);
        assertTrue(merged.contains("module.exports = {Conditions: {}};"), merged);
    }

    @Test
    @DisplayName("an entry is read out of a zip, and a missing entry is null")
    void readsBundledEntry() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("data/mods/cobblemon/conditions.js"));
            zip.write(COBBLEMON_CONDITIONS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertEquals(COBBLEMON_CONDITIONS, ShowdownModMerge.bundledEntry(
                new ByteArrayInputStream(bytes.toByteArray()), "data/mods/cobblemon/conditions.js"));
        assertNull(ShowdownModMerge.bundledEntry(
                new ByteArrayInputStream(bytes.toByteArray()), "data/mods/cobblemon/items.js"));
        assertNull(ShowdownModMerge.bundledEntry(null, "data/mods/cobblemon/items.js"));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) n++;
        return n;
    }
}
