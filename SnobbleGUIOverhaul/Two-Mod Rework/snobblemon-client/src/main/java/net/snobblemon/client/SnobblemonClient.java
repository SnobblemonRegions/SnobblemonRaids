package net.snobblemon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.snobblemon.client.gui.DefinedScreen;
import net.snobblemon.client.gui.UiScreen;
import net.snobblemon.client.theme.ThemeRegistry;
import net.snobblemon.proto.GuiSpec;
import net.snobblemon.proto.SnobbleProto;
import net.snobblemon.proto.UiSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snobblemon Client entry point: the generic side of the two-mod system. It announces itself to a Snobblemon Server, tracks which
 * art version the server's resource pack gave it, and draws server-defined GUIs. It decides nothing about the game.
 */
public final class SnobblemonClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SnobblemonClient");
    public static final String VERSION = FabricLoader.getInstance().getModContainer("snobblemon_client").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");

    /** What the server told us in its Welcome (null until then). Read by tests and diagnostics. */
    public static volatile SnobbleProto.Welcome welcome;
    static volatile net.snobblemon.client.gui.UiScreen hud;      // the HUD page being drawn over the world, if any

    @Override
    public void onInitializeClient() {
        SnobbleProto.registerTypes();
        WebguiAdvert.register();

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath("snobblemon_client", "theme"); }
            @Override public void onResourceManagerReload(ResourceManager rm) {
                ThemeRegistry.reload(rm);
                net.snobblemon.client.theme.FontBook.reload(rm);
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null && ClientPlayNetworking.canSend(SnobbleProto.PackState.TYPE)) reportPackState();
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            welcome = null;
            if (ClientPlayNetworking.canSend(SnobbleProto.Hello.TYPE)) ClientPlayNetworking.send(new SnobbleProto.Hello(SnobbleProto.PROTOCOL, VERSION));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> { welcome = null; hud = null; });

        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.Welcome.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            welcome = p;
            if (!p.accepted()) LOGGER.warn("Server refused this client: {}", p.reason());
            else if (!p.artVersion().equals(ThemeRegistry.version())) LOGGER.warn("Server art version '{}' differs from the loaded theme '{}'; the resource pack may still be applying", p.artVersion(), ThemeRegistry.version());
            reportPackState();
        }));

        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.GuiOpen.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (welcome == null || !welcome.accepted()) return;
            Minecraft mc = Minecraft.getInstance();
            try {
                GuiSpec spec = GuiSpec.parse(p.layoutJson());
                mc.setScreen(new DefinedScreen(spec, p.dataJson()));
            } catch (GuiSpec.Invalid e) {
                LOGGER.warn("Ignoring invalid GUI '{}': {}", p.guiId(), e.getMessage());
                mc.setScreen(DefinedScreen.error(p.guiId(), e.getMessage()));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.GuiUpdate.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().screen instanceof DefinedScreen s && s.guiId().equals(p.guiId())) s.update(p.dataJson(), p.message(), p.messageOk());
        }));
        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.GuiClose.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().screen instanceof DefinedScreen s && s.guiId().equals(p.guiId())) ctx.client().setScreen(null);
        }));

        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.UiOpen.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (welcome == null || !welcome.accepted()) return;
            Minecraft mc = Minecraft.getInstance();
            try {
                UiSpec spec = UiSpec.parse(p.layoutJson());
                // the page being replaced must not tell the server it closed: for the same page id that would close the page just opened
                if (mc.screen instanceof UiScreen old) old.closedByServer();
                mc.setScreen(new UiScreen(spec, p.dataJson()));
            } catch (UiSpec.Invalid e) {
                LOGGER.warn("Ignoring invalid page '{}': {}", p.pageId(), e.getMessage());
                mc.setScreen(DefinedScreen.error(p.pageId(), e.getMessage()));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.SpriteData.TYPE, (p, ctx) -> ctx.client().execute(() -> net.snobblemon.client.gui.PokemonSprites.received(p.key(), p.png())));
        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.UiHud.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (welcome == null || !welcome.accepted()) return;
            try {
                UiScreen h = new UiScreen(UiSpec.parse(p.layoutJson()), p.dataJson());
                h.asHud();
                hud = h;
            } catch (UiSpec.Invalid e) {
                LOGGER.warn("Ignoring invalid HUD page '{}': {}", p.pageId(), e.getMessage());
            }
        }));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((g, tick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (hud == null || mc.screen != null || mc.options.hideGui) return;
            try { hud.renderHud(g, mc); } catch (RuntimeException e) { LOGGER.warn("HUD page failed, hiding it: {}", e.toString()); hud = null; }
        });
        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.UiUpdate.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().screen instanceof UiScreen s && s.pageId().equals(p.pageId())) s.update(p.dataJson(), p.toast(), p.toastOk());
            else if (hud != null && hud.pageId().equals(p.pageId())) hud.update(p.dataJson(), p.toast(), p.toastOk());
        }));
        ClientPlayNetworking.registerGlobalReceiver(SnobbleProto.UiClose.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().screen instanceof UiScreen s && s.pageId().equals(p.pageId())) { s.closedByServer(); ctx.client().setScreen(null); }
            else if (hud != null && hud.pageId().equals(p.pageId())) hud = null;
        }));
    }

    public static void reportPackState() {
        if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(SnobbleProto.PackState.TYPE)) return;
        ClientPlayNetworking.send(new SnobbleProto.PackState(ThemeRegistry.version(), ThemeRegistry.namespace()));
    }
}
