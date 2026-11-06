package net.snobblemon.hubclient.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * The real Cobblemon model of a Pokemon wearing one skin, drawn on the same stage as the player preview. Cobblemon is
 * reached by reflection (it is installed next to this mod on a Cobblemon server), so a client without it, or a Cobblemon
 * version that renames something, just shows a text note instead of crashing. Drag to turn it, scroll to zoom.
 */
public final class PokemonPreview extends BaseComponent {
    private static final String GUI_UTILS = "com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt";
    private static boolean failed;
    private static String failure = "";
    private static Method draw;
    private static Constructor<?> renderableCtor, stateCtor;
    private static Method speciesLookup, partialTicksSetter;
    private static Object speciesRegistry;

    private final String species, aspect;
    private Object renderable, state;
    private boolean built;
    private float yaw = 35f;
    private float zoom = 1f;

    public PokemonPreview(String species, String aspect) {
        this.species = species;
        this.aspect = aspect;
        mouseDown().subscribe((mx, my, button) -> {
            if (my >= this.y + this.height - 16) {
                if (mx < this.x + 18) { yaw -= 30f; return true; }
                if (mx > this.x + this.width - 18) { yaw += 30f; return true; }
            }
            return true;
        });
        mouseDrag().subscribe((x, y, dx, dy, button) -> { yaw += (float) dx * 1.4f; return true; });
        mouseScroll().subscribe((x, y, amount) -> { zoom = Mth.clamp(zoom + (float) amount * 0.08f, 0.6f, 1.8f); return true; });
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return source == FocusSource.MOUSE_CLICK;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 64;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return 96;
    }

    private static void init() throws Exception {
        if (draw != null) return;
        Class<?> utils = Class.forName(GUI_UTILS);
        for (Method m : utils.getDeclaredMethods()) {
            if (m.getName().equals("drawProfilePokemon$default") && m.getParameterCount() == 18
                    && m.getParameterTypes()[0].getName().endsWith("RenderablePokemon")) draw = m;
        }
        if (draw == null) throw new IllegalStateException("drawProfilePokemon not found");
        Class<?> speciesClass = Class.forName("com.cobblemon.mod.common.pokemon.Species");
        Class<?> rp = Class.forName("com.cobblemon.mod.common.pokemon.RenderablePokemon");
        renderableCtor = rp.getConstructor(speciesClass, Set.class, ItemStack.class);
        Class<?> floating = Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.FloatingState");
        stateCtor = floating.getConstructor();
        Class<?> registry = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
        speciesRegistry = registry.getField("INSTANCE").get(null);
        for (Method m : registry.getMethods()) {
            if (m.getName().equals("getByIdentifier") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == ResourceLocation.class) speciesLookup = m;
        }
        if (speciesLookup == null) throw new IllegalStateException("PokemonSpecies.getByIdentifier not found");
        try { partialTicksSetter = floating.getMethod("updatePartialTicks", float.class); } catch (NoSuchMethodException ignored) { }
    }

    private void build() {
        built = true;
        try {
            init();
            ResourceLocation id = species.contains(":") ? ResourceLocation.parse(species) : ResourceLocation.fromNamespaceAndPath("cobblemon", species);
            Object sp = speciesLookup.invoke(speciesRegistry, id);
            if (sp == null) { failure = "unknown species " + id; return; }
            renderable = renderableCtor.newInstance(sp, aspect == null || aspect.isEmpty() ? Set.of() : Set.of(aspect), ItemStack.EMPTY);
            state = stateCtor.newInstance();
        } catch (Throwable t) {
            failed = true;
            failure = t.getClass().getSimpleName() + ": " + t.getMessage();
            net.snobblemon.hubclient.HubClient.LOGGER.warn("[preview] cannot draw Pokemon {}: {}", species, failure);
            renderable = null;
        }
    }

    @Override
    public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
        int x = this.x, y = this.y, w = this.width, h = this.height;
        String r = Theme.region();
        Theme.blit(ctx, Theme.sprite("stage_" + r), x, y, w, h);
        int d = Math.min(w, h) - 8;
        int d2 = Math.max(32, (int) (d * 0.95f));
        ctx.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
        Theme.tex(ctx, Theme.id("textures/gui/stage_disc_" + r + ".png"), x + (w - d2) / 2, y + (h - d2) / 2 - h / 14, d2, d2, 0f, 0f, 96, 96, 96, 96);
        ctx.disableScissor();
        int pw = Math.max(28, (int) (w * 0.62f));
        int ph = Math.max(7, pw / 4);
        Theme.tex(ctx, Theme.id("textures/gui/platform_" + r + ".png"), x + (w - pw) / 2, y + (int) (h * 0.80f), pw, ph, 0f, 0f, 56, 14, 56, 14);
        Theme.tex(ctx, Theme.icon("arrow_left"), x + 2, y + h - 16, 14, 14, 0f, 0f, 16, 16, 16, 16);
        Theme.tex(ctx, Theme.icon("arrow_right"), x + w - 16, y + h - 16, 14, 14, 0f, 0f, 16, 16, 16, 16);

        if (!built && !failed) build();
        if (renderable == null) {
            String msg = failed ? "Cannot show Pokemon" : "Unknown Pokemon";
            var font = Minecraft.getInstance().font;
            ctx.drawString(font, msg, x + (w - font.width(msg)) / 2, y + h / 2 - 4, 0xFFFFFFFF, true);
            return;
        }
        ctx.enableScissor(x, y, x + w, y + h);
        PoseStack ps = ctx.pose();
        ps.pushPose();
        ps.translate(x + w / 2f, y + h * 0.30f, 150.0);
        try {
            if (partialTicksSetter != null) partialTicksSetter.invoke(state, partialTicks);
            Quaternionf rot = new Quaternionf().rotateXYZ((float) Math.toRadians(13), (float) Math.toRadians(yaw), 0f);
            float scale = h * 0.32f * zoom;
            // pose, transform type, colour and the rest come from Cobblemon's own default mask
            int mask = 8 | 128 | 256 | 512 | 1024 | 2048 | 4096 | 8192 | 16384 | 32768;
            draw.invoke(null, renderable, ps, rot, null, state, partialTicks, scale, null, false, 1f, 1f, 1f, 1f, 0f, 0f, 0, mask, null);
        } catch (Throwable t) {
            failed = true;
            failure = t.toString();
            net.snobblemon.hubclient.HubClient.LOGGER.warn("[preview] Pokemon draw failed: {}", failure);
        }
        ps.popPose();
        Lighting.setupFor3DItems();
        ctx.disableScissor();
    }
}
