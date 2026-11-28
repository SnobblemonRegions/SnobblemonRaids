package com.cobbleraids.showdown;

import java.util.*;

/** Captures raw native registry source, including JS functions that JSON exports would lose. */
final class WorldBossRegistrySnapshot {
    record Operation(String method, String type, Map<String, String> data, String entry) {}
    private final List<Operation> operations = new ArrayList<>();
    private long bytes;
    private boolean speciesReady;
    synchronized void record(String method, String type, Map<String, String> data, String entry) {
        Map<String, String> copy = data == null ? Map.of() : Map.copyOf(data);
        if (method.equals("resetAllRegistries")) { operations.clear(); bytes = 0; speciesReady = false; }
        if (method.equals("resetRegistryData") && "species".equals(type)) speciesReady = false;
        long size = 128 + (entry == null ? 0 : (long) entry.length() * 2);
        for (var row : copy.entrySet()) size += 64 + (long) (row.getKey().length() + row.getValue().length()) * 2;
        if (operations.size() >= 20_000 || bytes + size > 32L * 1024 * 1024) throw new IllegalStateException("World-boss registry replay budget exceeded");
        operations.add(new Operation(method, type, copy, entry)); bytes += size;
    }
    synchronized void speciesReady() { speciesReady = true; }
    synchronized List<Operation> freeze() {
        if (!speciesReady) throw new IllegalStateException("Native species registry is not ready");
        return List.copyOf(operations);
    }
}
