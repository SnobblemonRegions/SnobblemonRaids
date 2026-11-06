package net.snobblemon.client.cosmetics;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/** What one player wears, plus the small animation state (wing flap, balloon) that is advanced once per client tick. */
public final class PlayerCosmetics {
    public final Map<String, CosmeticSpec> worn = new HashMap<>();
    public boolean fullSet;

    // wings
    float flapAmp, flapPhase, flapStep;
    Vec3 lastPos;
    boolean air;

    // balloon
    Vec3 balloonPos, balloonPrev, balloonVel = Vec3.ZERO;

    public boolean isEmpty() {
        return worn.isEmpty();
    }

    public CosmeticSpec get(String slot) {
        return worn.get(slot);
    }

    void recompute() {
        EnumSet<CosmeticSpec.Anchor> anchors = EnumSet.noneOf(CosmeticSpec.Anchor.class);
        for (CosmeticSpec s : worn.values()) if (s.isArmor()) for (CosmeticSpec.Part p : s.parts) anchors.add(p.anchor());
        fullSet = anchors.containsAll(EnumSet.allOf(CosmeticSpec.Anchor.class));
        flapAmp = 0;
        balloonPos = null;
        balloonPrev = null;
        balloonVel = Vec3.ZERO;
    }

    /** True when a cosmetic stands in for the real armor piece in this slot (the real piece is simply not drawn). */
    public boolean replaces(EquipmentSlot slot) {
        String key = switch (slot) {
            case HEAD -> "armor_head";
            case CHEST -> "armor_chest";
            case LEGS -> "armor_legs";
            case FEET -> "armor_feet";
            default -> null;
        };
        return key != null && worn.containsKey(key);
    }

    /** Called every client tick for players whose loadout needs per-tick animation. */
    void tick(AbstractClientPlayer p) {
        CosmeticSpec back = worn.get("back");
        if (back != null && back.wings != null) {
            CosmeticSpec.Wings w = back.wings;
            Vec3 pos = p.position();
            double moved = lastPos == null ? 0 : Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z);
            lastPos = pos;
            air = !p.onGround() && !p.isInWater() && !p.isPassenger();
            float target = air ? 1f : Math.min(1f, w.idle() + (float) moved * 2.5f);
            flapAmp += (target - flapAmp) * 0.15f;
            flapStep = (float) ((Math.PI * 2) / (air ? w.ticks() * 0.6 : w.ticks()));
            flapPhase += flapStep;
            if (flapPhase > Math.PI * 2) flapPhase -= (float) (Math.PI * 2);
        }

        CosmeticSpec balloon = worn.get("balloon");
        if (balloon != null) {
            Vec3 base = p.position();
            double bob = Math.sin(p.tickCount / 12.0) * 0.06;
            double rad = Math.toRadians(p.yBodyRot);
            Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
            Vec3 target = base.add(0, 2.55 + balloon.offY + bob, 0).add(fwd.scale(-0.45 + balloon.offZ)).add(right.scale(balloon.offX));
            if (balloonPos == null || balloonPos.distanceToSqr(target) > 64) {
                balloonPos = target;
                balloonVel = Vec3.ZERO;
            }
            balloonPrev = balloonPos;
            balloonVel = balloonVel.add(target.subtract(balloonPos).scale(0.12)).scale(0.78);
            balloonPos = balloonPos.add(balloonVel);
        }
    }

    boolean needsTick() {
        CosmeticSpec back = worn.get("back");
        return worn.containsKey("balloon") || (back != null && back.wings != null);
    }
}
