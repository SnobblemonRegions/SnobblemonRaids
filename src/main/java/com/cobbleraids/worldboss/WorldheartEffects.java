package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Authored Distortion v1: converging seal, rising double helix, then a quiet orbiting crest. */
final class WorldheartEffects {
    static final String APPEARANCE = "distortion_v1";
    private static final Set<String> SPECIES = Set.of("cobblemon:lucario", "cobblemon:charizard", "cobblemon:gengar", "cobblemon:eevee", "cobblemon:gardevoir", "cobblemon:dragonite");
    record Mote(double x, double y, double z, boolean accent) {}
    static boolean supported(String species, String appearance) { return APPEARANCE.equals(appearance) && SPECIES.contains(species); }
    static List<Mote> frame(int age, int style, double width, double height, boolean minimal) {
        if (age < 0 || style < 0 || style > 2 || !Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid Worldheart frame");
        double radius = Math.max(0.35, Math.min(1.8, width * 0.65)); height = Math.max(0.5, Math.min(3, height));
        var motes = new ArrayList<Mote>();
        if (age < 40 && age % 2 == 0 && (!minimal || age % 10 == 0)) {
            int count = minimal ? 4 : 12;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count + age * 0.13;
                double r = age < 12 ? radius * (2.0 - age / 12.0) : radius * (0.7 + 0.3 * Math.cos(angle * 3));
                double y = age < 12 ? 0.12 : 0.15 + height * (age - 12) / 28.0;
                if (age >= 12) {
                    double section = (i / 2) / (count / 2.0), rise = (age - 12) / 28.0;
                    angle = section * Math.PI * 2 + age * 0.19 + (i % 2) * Math.PI;
                    y = 0.15 + height * ((section + rise) % 1.0);
                }
                motes.add(new Mote(Math.cos(angle) * r, y, Math.sin(angle) * r, i % 3 == 0));
            }
        } else if (age >= 40 && age % (minimal ? 20 : 10) == 0) {
            int count = minimal ? 1 : style + 1;
            for (int i = 0; i < count; i++) { double angle = age * 0.08 + Math.PI * 2 * i / count;
                motes.add(new Mote(Math.cos(angle) * radius, height * 0.5 + 0.12 * Math.sin(angle * 2), Math.sin(angle) * radius, i == 0)); }
        }
        return List.copyOf(motes);
    }
    private WorldheartEffects() {}
}
