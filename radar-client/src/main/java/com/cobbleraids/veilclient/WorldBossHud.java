package com.cobbleraids.veilclient;

import com.cobbleraids.visual.protocol.BossHudPayload;
import com.cobbleraids.visual.protocol.VisualProtocol;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

/** A single owned HUD, with native bars retained whenever metadata expires or rendering fails. */
public final class WorldBossHud {
    private static final ResourceLocation FRAME = art("rift_crown"), HEALTH = art("rift_health"),
            WOUNDED = art("rift_wounded"), CRITICAL = art("rift_critical"), FLAIR = art("rift_crown_flair");
    private static final BossHudMotion MOTION = new BossHudMotion();
    private static BossHudPayload frame;
    private static Object world;
    private static long received, sequence = -1;
    private static boolean failed;
    private WorldBossHud() {}
    private static ResourceLocation art(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobbleraids_radar", "textures/worldboss/" + name + ".png");
    }
    static void register() {
        VisualProtocol.once("worldboss-hud", () -> PayloadTypeRegistry.playS2C().register(BossHudPayload.TYPE, BossHudPayload.CODEC));
        ClientPlayNetworking.registerGlobalReceiver(BossHudPayload.TYPE, (p,c) -> c.client().execute(() -> {
            if (p.sequence() <= sequence) return;
            sequence = p.sequence(); frame = p.active() ? p : null;
            world = c.client().level; received = DistortionVeilClient.millis();
            if (!p.active()) MOTION.clear();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> {
            frame = null; world = null; sequence = -1; failed = false; MOTION.clear();
        });
    }
    public static UUID owned() {
        var mc = Minecraft.getInstance();
        if (failed || frame == null) return null;
        if (world != mc.level || mc.level == null || !mc.level.dimension().location().toString().equals(frame.dimension())
                || DistortionVeilClient.millis() - received > 6000) {
            frame = null; MOTION.clear(); return null;
        }
        return frame.bar();
    }
    /** Returns the space reserved above any remaining vanilla bars. */
    public static int draw(GuiGraphics gui, BossEvent event) {
        if (!event.getId().equals(owned())) return 0;
        if (gui.guiWidth()<240) return 0; // Tiny windows keep the native bar, without disabling the renderer.
        var layout=RiftCrownLayout.at(gui.guiWidth());
        int rows=1;
        var pose = gui.pose();
        pose.pushPose();
        try {
            // Same blend contract as the native BossHealthOverlay.drawBar implementation.
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            // Source artwork is imported unchanged. UV bounds exclude only its transparent margin.
            gui.blit(FRAME,layout.x(),layout.y(),layout.width(),layout.height(),8,24,2156,584,2172,724);
            var state = MOTION.update(event.getId(), event.getProgress(), DistortionVeilClient.millis(), frame.detail() == 2);
            int left=layout.railX(),top=layout.railY(),width=layout.railWidth(),height=layout.railHeight();
            // An opaque inset seam keeps the sparse animation legible over any world background.
            gui.fill(left,top-2,left+width,top-1,0xFF65437B);
            int flair = BossHudFlair.row(DistortionVeilClient.millis(),frame.detail(),state.health() <= .2);
            if (flair >= 0) gui.blit(FLAIR,layout.x(),layout.y(),layout.width(),layout.height(),0,flair*96,360,96,360,1536);
            int health = (int) Math.round(width * state.health()), echo = (int) Math.round(width * state.echo());
            if (echo > health) {
                gui.fill(left+health,top,left+echo,top+height,0xFF92836E);
                gui.fill(left+health,top,left+echo,top+1,0xFFE0CBA3);
            }
            if (health > 0) {
                // Native-resolution pixel strip: crop its remaining width, never stretch or blur it.
                var strip = state.health() <= .2 ? CRITICAL : state.health() <= .5 ? WOUNDED : HEALTH;
                gui.blit(strip,left,top,health,height,0,0,Math.max(1,(int)Math.round(189*state.health())),6,189,6);
            }
            var font=Minecraft.getInstance().font;
            String title=frame.title().toUpperCase(java.util.Locale.ROOT);
            float titleScale=layout.width()>=336 && font.width(Component.literal(title).withStyle(s->s.withBold(true)))*1.5<=layout.nameWidth() ? 1.5f : 1;
            centered(gui,title,layout.center(),layout.titleY(),layout.nameWidth(),0xFFF0CD,true,titleScale);
            String status = BossHudText.status(frame.status());
            String numbers=compact(state.health()*frame.maximum())+"/"+compact(frame.maximum())+" · "+Math.round(state.health()*100)+"%";
            int numbersWidth=font.width(numbers),statusWidth=font.width(status);
            rows=layout.rows(numbersWidth,statusWidth,frame.preparing());
            gui.fill(left-1,layout.infoY()-1,left+width+1,layout.infoY()+rows*10-1,0xE8181024);
            if (rows>1) gui.fill(left-1,layout.infoY()+rows*10-1,left+width+1,layout.infoY()+rows*10,0xFF594169);
            if (!frame.preparing()) {
                if(rows==1) {
                    text(gui,numbers,left,layout.infoY(),width,0xEEEEF0,false,false);
                    text(gui,status,left+width,layout.infoY(),width-numbersWidth-8,0xDDDDE4,false,true);
                } else {
                    centered(gui,numbers,layout.center(),layout.infoY(),width,0xEEEEF0,false,1);
                    centered(gui,status,layout.center(),layout.infoY()+10,width,0xDDDDE4,false,1);
                }
            } else {
                String[] captions=rows>1 ? BossHudText.stack(status) : new String[]{status};
                for(int row=0;row<captions.length;row++)
                    centered(gui,captions[row],layout.center(),layout.infoY()+row*10,width,0xDDDDE4,false,1);
            }
        } finally { RenderSystem.disableBlend(); pose.popPose(); }
        return layout.reserve(rows);
    }
    private static void centered(GuiGraphics gui,String value,int center,int y,int width,int color,boolean bold,float scale) {
        var font=Minecraft.getInstance().font;
        var styled=Component.literal(value).withStyle(s->s.withBold(bold));
        var ordered=Language.getInstance().getVisualOrder(font.substrByWidth(styled,(int)(width/scale)));
        gui.pose().pushPose();
        try {
            gui.pose().translate(center,y,0);gui.pose().scale(scale,scale,1);
            gui.drawString(font,ordered,-font.width(ordered)/2,0,color,true);
        } finally { gui.pose().popPose(); }
    }
    private static void text(GuiGraphics gui,String value,int x,int y,int maxWidth,int color,boolean bold,boolean right) {
        var font = Minecraft.getInstance().font;
        var styled = Component.literal(value).withStyle(style -> style.withBold(bold));
        var ordered = Language.getInstance().getVisualOrder(font.substrByWidth(styled,maxWidth));
        gui.drawString(font,ordered,right ? x-font.width(ordered) : x,y,color,true);
    }
    private static String compact(double value) {
        if (value < 1000) return Long.toString(Math.round(value));
        double unit = value >= 1_000_000 ? 1_000_000 : 1000;
        return String.format(java.util.Locale.ROOT,"%.1f%s",value/unit,unit==1000?"k":"M").replace(".0","");
    }
    public static void fault(RuntimeException error) {
        failed = true; MOTION.clear();
        DistortionVeilClient.reportFault("World Boss HUD (native bar restored)",error);
    }
}
