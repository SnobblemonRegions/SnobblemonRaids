package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Daily Boss's seal ({@link DailySeal}) on the ground under the boss.
 *
 * <p>The seal itself is two flat, full-bright display panels lying on the ground (art: {@code tools/daily_seal_art.py}): an outer panel with
 * the rims, runes and spokes, and an inner one with the inner ring and core. They are drawn as rows of glowing dots, so the seal reads as a
 * particle sigil, but they stay crisp and bright at any distance and by night, which real dust does not (the client randomises each dust
 * particle's brightness and lights it by the world). The panels are never added to the world: like the dome's, they are sent only to players
 * near the zone. Real particles ride on top: while players gather, a bright comet runs round the rim as the countdown (and the inner panel
 * appears halfway); when the fight opens the seal ignites from bronze to gold with a burst of sparks and a chime, and the two panels turn in
 * opposite directions (the client interpolates, so the turn is smooth); when the boss burns low the seal turns ember red, faster, with
 * flames lifting off the rim.
 *
 * <p>Server thread only.
 */
final class WorldBossSeal {

    /** The panels' models, registered by {@link DailyArenaPack}: "outer_gold" and so on, to a vanilla item and custom model number. */
    private static final Map<String, Model> MODELS = new HashMap<>();

    record Model(Item item, int number) {}

    /** Players further than this from the seal's centre are not sent it. */
    private static final double VIEW = 160.0;
    /** How often the viewers are checked, and the turn is re-aimed (the client interpolates across each step). */
    private static final int VIEWERS_EVERY = 20;
    private static final int TURN_EVERY = 40;
    /** Lifted this far above the boss's footing so the panels do not flicker against the ground. */
    private static final double LIFT = 0.04;
    private static final float OUTER_SIZE = 62f;
    private static final float INNER_SIZE = 28f;
    /** The inner panel turns this fast against the outer one, the other way. */
    private static final double INNER_RATE = -0.6;
    private static final Vector3f COMET = new Vector3f(1.0f, 0.90f, 0.62f);

    private static String builtFor = "";
    private static ServerLevel level;
    private static Entity outer;
    private static Entity inner;
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static String colouring = "";
    private static boolean innerShown;
    private static double outerTurn;
    private static double innerTurn;
    private static DailySeal.Stage lastStage;
    private static long ticks;

    private WorldBossSeal() {}

    static void registerModel(String key, Item item, int number) {
        MODELS.put(key, new Model(item, number));
    }

    /** The panel models the pack must provide: outer and inner, in bronze, gold and ember. */
    static List<String> modelKeys() {
        List<String> keys = new ArrayList<>();
        for (String part : new String[] {"outer", "inner"}) {
            for (String colour : new String[] {"bronze", "gold", "ember"}) keys.add(part + "_" + colour);
        }
        return keys;
    }

    /** The vanilla item the panels borrow (the pack maps custom model numbers on it to the seal's models). */
    static Item baseItem() {
        return Items.GLOWSTONE_DUST;
    }

    /**
     * Called every server tick while a Daily Boss is up.
     *
     * @param gatherSecondsLeft seconds left in the gather countdown (0 or less once the fight is open)
     */
    static void tick(MinecraftServer server, WorldBossState state, int gatherSecondsLeft, int countdownSeconds) {
        ticks++;
        if (state.pool() == null || MODELS.isEmpty()) return;
        ResourceLocation dimensionId = ResourceLocation.tryParse(state.dimension());
        ServerLevel at = dimensionId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (at == null) return;
        String key = state.bossId() + "|" + state.dimension();
        if (!key.equals(builtFor) || at != level) build(server, at, key);

        float share = state.pool().max() <= 0f ? 0f : state.pool().current() / state.pool().max();
        DailySeal.Phase phase = DailySeal.phase(gatherSecondsLeft, countdownSeconds, share);
        String want = switch (phase.stage()) {
            case GATHER -> "bronze";
            case OPEN -> "gold";
            case LOW -> "ember";
        };
        boolean ignite = lastStage == DailySeal.Stage.GATHER && phase.stage() != DailySeal.Stage.GATHER;
        boolean showInner = phase.stage() != DailySeal.Stage.GATHER || phase.progress() >= 0.5;
        boolean restyle = !want.equals(colouring) || showInner != innerShown;
        boolean turn = ticks % TURN_EVERY == 0 && phase.stage() != DailySeal.Stage.GATHER;
        if (turn) {
            double step = DailySeal.angle(phase.stage(), TURN_EVERY);
            outerTurn += step;
            innerTurn += step * INNER_RATE;
        }
        if (restyle || turn) {
            int glide = restyle ? 30 : TURN_EVERY;
            colouring = want;
            innerShown = showInner;
            dress(outer, "outer_" + want, OUTER_SIZE, outerTurn, glide);
            dress(inner, "inner_" + want, showInner ? INNER_SIZE : 0.01f, innerTurn, glide);
            sendData(server, outer);
            sendData(server, inner);
        }
        lastStage = phase.stage();
        if (ticks % VIEWERS_EVERY == 0 || VIEWERS.isEmpty()) updateViewers(server, state);
        if (ignite) ignite(server, state);
        sparks(server, state, phase);
    }

    /** Takes the seal away from everyone: the boss left or fell, or the zone visuals were turned off. */
    static void clear(MinecraftServer server) {
        if (outer != null && !VIEWERS.isEmpty()) {
            for (UUID id : VIEWERS) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) player.connection.send(new ClientboundRemoveEntitiesPacket(outer.getId(), inner.getId()));
            }
        }
        reset();
    }

    /** Forgets everything, for the server-stopped hook (nobody is left to tell). */
    static void reset() {
        VIEWERS.clear();
        builtFor = "";
        level = null;
        outer = null;
        inner = null;
        colouring = "";
        innerShown = false;
        outerTurn = 0;
        innerTurn = 0;
        lastStage = null;
    }

    // ---- the panels ----

    private static void build(MinecraftServer server, ServerLevel at, String key) {
        clear(server);
        builtFor = key;
        level = at;
        outer = EntityType.ITEM_DISPLAY.create(at);
        inner = EntityType.ITEM_DISPLAY.create(at);
        lastStage = DailySeal.Stage.GATHER;
    }

    /** Sets a panel's look: which model, how big, how far turned, and over how many ticks the client glides there. */
    private static void dress(Entity panel, String model, float size, double turn, int glide) {
        Model m = MODELS.get(model);
        if (m == null) return;
        var stack = m.item().getDefaultInstance();
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(m.number()));
        CompoundTag tag = new CompoundTag();
        tag.put("item", stack.save(level.registryAccess()));
        tag.put("item_display", StringTag.valueOf("none"));
        // the quad stands in the XY plane: tip it flat (face up), then turn it about the vertical
        Quaternionf rotation = new Quaternionf().rotateY((float) turn).rotateX((float) (-Math.PI / 2));
        CompoundTag transformation = new CompoundTag();
        transformation.put("left_rotation", floats(rotation.x, rotation.y, rotation.z, rotation.w));
        transformation.put("right_rotation", floats(0f, 0f, 0f, 1f));
        transformation.put("scale", floats(size, size, 1f));
        transformation.put("translation", floats(0f, 0f, 0f));
        tag.put("transformation", transformation);
        tag.put("interpolation_duration", IntTag.valueOf(glide));
        tag.put("start_interpolation", IntTag.valueOf(0));
        tag.put("view_range", FloatTag.valueOf(4.0f));
        tag.put("width", FloatTag.valueOf(OUTER_SIZE));
        tag.put("height", FloatTag.valueOf(1.0f));
        CompoundTag brightness = new CompoundTag();
        brightness.put("sky", IntTag.valueOf(15));
        brightness.put("block", IntTag.valueOf(15));
        tag.put("brightness", brightness);
        Vec3 keep = panel.position();
        panel.load(tag);          // load() also reads a (missing) position, so the panel's place is put back after it
        panel.setPos(keep);
    }

    private static void updateViewers(MinecraftServer server, WorldBossState state) {
        Vec3 spot = new Vec3(state.x(), state.y() + LIFT, state.z());
        outer.setPos(spot);
        inner.setPos(spot.add(0, 0.02, 0));
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            boolean near = player.level() == level && Math.hypot(player.getX() - state.x(), player.getZ() - state.z()) <= VIEW;
            if (near && VIEWERS.add(id)) {
                List<Packet<? super ClientGamePacketListener>> add = new ArrayList<>();
                for (Entity panel : new Entity[] {outer, inner}) {
                    Vec3 p = panel.position();
                    add.add(new ClientboundAddEntityPacket(panel.getId(), panel.getUUID(), p.x, p.y, p.z, 0f, 0f, panel.getType(), 0, Vec3.ZERO, 0.0));
                    List<SynchedEntityData.DataValue<?>> data = panel.getEntityData().getNonDefaultValues();
                    if (data != null) add.add(new ClientboundSetEntityDataPacket(panel.getId(), data));
                }
                player.connection.send(new ClientboundBundlePacket(add));
            } else if (!near && VIEWERS.remove(id)) {
                player.connection.send(new ClientboundRemoveEntitiesPacket(outer.getId(), inner.getId()));
            }
        }
        VIEWERS.retainAll(online);
    }

    private static void sendData(MinecraftServer server, Entity panel) {
        List<SynchedEntityData.DataValue<?>> dirty = panel.getEntityData().packDirty();
        if (dirty == null) return;
        ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(panel.getId(), dirty);
        for (UUID id : VIEWERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.connection.send(packet);
        }
    }

    // ---- particles on top ----

    /** The moment the fight opens: a ring of sparks bursts up off the rim, with a chime and a low whoomp for everyone near. */
    private static void ignite(MinecraftServer server, WorldBossState state) {
        List<Packet<? super ClientGamePacketListener>> burst = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            double a = 2 * Math.PI * i / 160;
            double x = state.x() + DailySeal.RADIUS * Math.sin(a);
            double z = state.z() + DailySeal.RADIUS * Math.cos(a);
            burst.add(particle(ParticleTypes.WAX_ON, x, state.y() + 0.4, z, 0.6f, 0.9f));
            if (i % 4 == 0) burst.add(particle(ParticleTypes.END_ROD, x, state.y() + 0.3, z, 0.10f, 1f));
        }
        for (UUID id : VIEWERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            player.connection.send(new ClientboundBundlePacket(burst));
            Vec3 at = player.position();
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_RESONATE),
                    SoundSource.AMBIENT, at.x, at.y, at.z, 1.0f, 0.7f, player.getRandom().nextLong()));
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.FIRECHARGE_USE),
                    SoundSource.AMBIENT, at.x, at.y, at.z, 0.6f, 0.6f, player.getRandom().nextLong()));
        }
    }

    /** Gathering: a comet running round the rim as the countdown. Open: sparks twinkling over the seal. Low: more sparks and rising flames. */
    private static void sparks(MinecraftServer server, WorldBossState state, DailySeal.Phase phase) {
        if (VIEWERS.isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        double y = state.y() + 0.35;
        if (phase.stage() == DailySeal.Stage.GATHER) {
            if (ticks % 2 != 0) return;
            double a = 2 * Math.PI * phase.progress();
            for (int k = 0; k < 5; k++) {   // the head and a short fading tail behind it
                double b = a - k * 0.012;
                double x = state.x() + DailySeal.RADIUS * Math.sin(b);
                double z = state.z() + DailySeal.RADIUS * Math.cos(b);
                packets.add(particle(new DustParticleOptions(COMET, 4.0f - k * 0.6f), x, y, z, 0f, 0f));
                if (k == 0) packets.add(particle(ParticleTypes.WAX_ON, x, y + 0.2, z, 0f, 0f));
            }
            if (ticks % 6 == 0) {
                double x = state.x() + DailySeal.RADIUS * Math.sin(a);
                double z = state.z() + DailySeal.RADIUS * Math.cos(a);
                packets.add(particle(ParticleTypes.END_ROD, x, y, z, 0.04f, 1f));
            }
        } else {
            if (ticks % 5 != 0) return;
            boolean low = phase.stage() == DailySeal.Stage.LOW;
            for (int k = 0; k < (low ? 26 : 18); k++) {
                double a = random.nextDouble() * 2 * Math.PI;
                double r = random.nextDouble() < 0.7 ? DailySeal.RADIUS - random.nextDouble() * 2.6 : 12.0 + random.nextDouble() * 0.6;
                packets.add(particle(ParticleTypes.WAX_ON, state.x() + r * Math.sin(a), y, state.z() + r * Math.cos(a), 0f, 0f));
            }
            if (low) {
                for (int k = 0; k < 10; k++) {
                    double a = random.nextDouble() * 2 * Math.PI;
                    double x = state.x() + DailySeal.RADIUS * Math.sin(a);
                    double z = state.z() + DailySeal.RADIUS * Math.cos(a);
                    packets.add(particle(ParticleTypes.SMALL_FLAME, x, state.y() + 0.2, z, 0.04f + random.nextFloat() * 0.05f, 1f));
                }
            }
        }
        if (packets.isEmpty()) return;
        ClientboundBundlePacket bundle = new ClientboundBundlePacket(packets);
        for (UUID id : VIEWERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.connection.send(bundle);
        }
    }

    /** One long-distance particle, at rest ({@code rise} 0) or drifting up at {@code rise * speed} blocks a tick. */
    private static ClientboundLevelParticlesPacket particle(ParticleOptions options, double x, double y, double z, float rise, float speed) {
        return new ClientboundLevelParticlesPacket(options, true, x, y, z, 0f, rise, 0f, speed, 0);
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }
}
