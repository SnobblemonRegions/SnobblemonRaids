package com.cobbleraids.radarprobe;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import com.cobbleraids.veilclient.WorldBossHud;
import com.cobbleraids.visual.protocol.BossHudPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
/** Disposable native render capture. No input injection; excluded from the player companion. */
public final class VeilCaptureProbe implements ClientModInitializer {
 private String previous = "", previousHud = ""; private int ticks;
 private int restoreWidth, restoreHeight, restoreGuiScale;
 private static final java.util.UUID EXTRA = new java.util.UUID(487,99);
 public void onInitializeClient() {
  if (!Boolean.getBoolean("cobbleraids.veil.capture")) return;
  Path output = Path.of(System.getProperty("cobbleraids.veil.capture.output"));
  WorldRenderEvents.START.register(context -> {
   // Explicit client-only presentation fixtures, excluded from every player/server artifact.
   // The live server pool and its opening rules are never changed by these captures.
   try {
    Path mode = output.resolve("hud-mode.txt");
    if (!Files.exists(mode)) return;
    String value = Files.readString(mode).strip();
    if (value.equals("combat") || value.equals("crowded")) {
     var field = WorldBossHud.class.getDeclaredField("frame"); field.setAccessible(true);
     var p = (BossHudPayload) field.get(null);
     if (p != null) field.set(null,new BossHudPayload(p.sequence(),p.active(),p.bar(),p.dimension(),
             value.equals("crowded")?"Giratina III":p.title(),
             value.equals("crowded")?"50 FIGHTING  /  14m REMAINING":"COMBAT PRESENTATION FIXTURE",8000,8000,p.detail(),false));
    } else if (value.equals("stale")) {
     var field = WorldBossHud.class.getDeclaredField("received");field.setAccessible(true);field.setLong(null,0);
    }
   } catch(Exception failure) {
    try { Files.writeString(output.resolve("hud-fixture-failure.txt"),failure.toString()); } catch(Exception ignored) {}
   }
  });
  ClientTickEvents.END_CLIENT_TICK.register(client -> {
   if (++ticks % 10 != 0 || client.player == null || client.level == null) return;
   try {
    // Local presentation fixture only: never changes the server pool, contribution or rewards.
    Path hudRequest = output.resolve("hud-command.txt");
    if (Files.exists(hudRequest)) {
     String command = Files.readString(hudRequest).strip();
     if (!command.equals(previousHud)) {
      var id = WorldBossHud.owned();
      if (command.startsWith("progress:") && id != null) {
       float progress = Float.parseFloat(command.split(":")[1]);
       if (!Float.isFinite(progress) || progress < 0 || progress > 1) throw new IllegalArgumentException("Fixture progress");
       var event = new BossEvent(id,Component.literal("fixture"),BossEvent.BossBarColor.BLUE,BossEvent.BossBarOverlay.PROGRESS) {};
       event.setProgress(progress);client.gui.getBossOverlay().update(ClientboundBossEventPacket.createUpdateProgressPacket(event));
      } else if (command.equals("extra:on")) {
       var event = new BossEvent(EXTRA,Component.literal("Independent boss bar — compatibility fixture"),BossEvent.BossBarColor.PINK,BossEvent.BossBarOverlay.PROGRESS) {};
       event.setProgress(.7f);client.gui.getBossOverlay().update(ClientboundBossEventPacket.createAddPacket(event));
      } else if (command.equals("extra:off")) client.gui.getBossOverlay().update(ClientboundBossEventPacket.createRemovePacket(EXTRA));
      else if(command.equals("viewport:small")) {
       if(restoreWidth==0) {
        restoreWidth=client.getWindow().getWidth();restoreHeight=client.getWindow().getHeight();
        restoreGuiScale=client.options.guiScale().get();
       }
       client.getWindow().setWindowed(960,720);client.options.guiScale().set(3);client.resizeDisplay();
      } else if(command.equals("viewport:restore") && restoreWidth>0) {
       client.getWindow().setWindowed(restoreWidth,restoreHeight);client.options.guiScale().set(restoreGuiScale);client.resizeDisplay();
       restoreWidth=0;
      }
      previousHud = command;
     }
    }
    Path request = output.resolve("request.txt");
    if (!Files.exists(request)) return;
    String name = Files.readString(request).strip();
    if (name.equals(previous) || !name.matches("[a-z0-9-]{1,64}\\.png")) return;
    client.setScreen(null); client.getToasts().clear();
    Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), message -> {});
    var accessor = Class.forName("com.cobbleraids.veilclient.DistortionVeilClient").getDeclaredMethod("frame");
    accessor.setAccessible(true);
    var frame = (com.cobbleraids.visual.protocol.VeilPayload) accessor.invoke(null);
    if (frame != null) {
     var boss = client.level.getEntity(frame.entityId());
     if (boss != null) Files.writeString(output.resolve(name + ".txt"), "guiWidth="+client.getWindow().getGuiScaledWidth()+" guiHeight="+client.getWindow().getGuiScaledHeight()+" hudOwner=" + WorldBossHud.owned() + " width=" + boss.getBbWidth() + " height=" + boss.getBbHeight() + " position=" + boss.position() + " render=" + boss.shouldRenderAtSqrDistance(boss.distanceToSqr(client.player)) + " bounds=" + boss.getBoundingBoxForCulling() + " " + com.cobbleraids.veilclient.WingAttachments.diagnostics());
    }
    previous = name;
   } catch (Exception error) {
    try { Files.writeString(output.resolve("failure.txt"), error.toString()); } catch (Exception ignored) {}
   }
  });
 }
}
