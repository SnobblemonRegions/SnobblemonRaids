package com.cobbleraids.visual.protocol;

import java.util.HashSet;
import java.util.Set;

/** Shared class identity makes common/client initializers safe in the same JVM. */
public final class VisualProtocol {
    private static final Set<String> REGISTERED = new HashSet<>();
    private VisualProtocol() {}
    public static synchronized void once(String id, Runnable registration) {
        if (REGISTERED.contains(id)) return;
        registration.run();
        REGISTERED.add(id);
    }
}
