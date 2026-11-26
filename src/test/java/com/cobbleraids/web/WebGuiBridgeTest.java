package com.cobbleraids.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Every raid page keeps the native page id the compat jar gave it, so the layouts in Snobblemon GUI still match. */
class WebGuiBridgeTest {

    /** The seven pages this mod opens, each the layout SnobbleGUIOverhaul ships for it. */
    @Test
    void everyRaidPageMapsToItsNativeLayout() {
        for (String page : new String[] {"capture", "hub", "leaders", "legends", "reveal", "shop", "trophy"}) {
            assertEquals("cobbleraids_" + page, WebGuiBridge.pageId(WebUi.pageUrl(page + "/" + page + ".html")));
        }
    }

    @Test
    void queryFragmentSchemeAndPathTricksAreDropped() {
        assertEquals("cobbleraids_shop", WebGuiBridge.pageId("webgui://cobbleraids/shop/shop.html?tab=2#top"));
        assertEquals("cobbleraids_shop", WebGuiBridge.pageId("webgui:/cobbleraids/shop/shop.html"));
        assertEquals("cobbleraids_hub", WebGuiBridge.pageId("webgui://cobbleraids/../hub/hub.html"));
        assertEquals("cobbleraids", WebGuiBridge.pageId("webgui://cobbleraids/index.html"));
        assertEquals("", WebGuiBridge.pageId(null));
    }
}
