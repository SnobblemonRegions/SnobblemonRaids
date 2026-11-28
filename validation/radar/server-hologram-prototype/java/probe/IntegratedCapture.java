package probe;

import com.bedrockk.molang.MoLang;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import probe.mixin.UseItemInvoker;

final class IntegratedCapture {
    private final Path output;
    private int stage;
    private int delay;
    private int terrainEntity;
    private int carrierEntity;
    private Vec3 initialPosition;
    private int readoutEntity;
    private int captureAttempts;
    private boolean positionedForCapture;

    IntegratedCapture(Path output) { this.output = output; }

    void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try { tick(client); }
            catch (Exception error) {
                try { Files.writeString(output.resolve("failure.txt"), error.toString()); }
                catch (java.io.IOException ignored) {}
                client.stop();
            }
        });
    }

    private void tick(Minecraft client) throws Exception {
        if (client.screen instanceof ConfirmScreen screen
                && screen.getNarrationMessage().getString().contains("Raid projector test pack")) {
            for (var child : screen.children()) {
                if (child instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("Yes"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (client.player == null || client.level == null || client.gameMode == null
                || !Files.exists(output.resolve("integrated-ready"))) return;
        client.getToasts().clear();
        if (stage == 2 && client.screen == null && client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.releaseMouse();
        }
        if (client.getOverlay() != null || delay-- > 0) return;
        switch (stage) {
            case 0 -> {
                if (client.screen != null || !client.player.getInventory().getItem(0).getHoverName().getString().equals("Raid Radar")) return;
                client.player.getInventory().selected = 0;
                client.getConnection().send(new ServerboundSetCarriedItemPacket(0));
                client.player.getAbilities().flying = true;
                client.player.onUpdateAbilities();
                client.mouseHandler.releaseMouse();
                client.player.setYRot(180);
                client.player.setXRot(14);
                stage = 8;
                delay = 12;
            }
            case 8 -> {
                rightClick(client);
                stage = 1;
                delay = 12;
            }
            case 1 -> {
                if (!(client.player.containerMenu instanceof ChestMenu)) throw new IllegalStateException("Radar menu did not open");
                click(client, 20);
                stage = 2;
                delay = 80;
            }
            case 2 -> {
                for (var entity : client.level.entitiesForRendering()) {
                    if (!(entity instanceof GenericBedrockEntity projector) || !projector.getCategory().toString().equals("cobbleraids:radar_projector")) continue;
                    if (!positionedForCapture) {
                        // Keep external mouse movement and the menu's cursor recentering out of the capture.
                        client.mouseHandler.releaseMouse();
                        client.gui.getChat().clearMessages(false);
                        positionedForCapture = true;
                        delay = 16;
                        return;
                    }
                    if (!(projector.getDelegate() instanceof PosableState state) || state.getCurrentModel() == null) continue;
                    int known = 0;
                    var bands = new java.util.HashSet<Double>();
                    for (int i = 0; i < 256; i++) {
                        double band = value(state, "h" + i);
                        if (band > 0) {
                            known++;
                            bands.add(band);
                        }
                    }
                    if (known < 60 || bands.size() < 2 || value(state, "target") != 1) throw new IllegalStateException("Missing live terrain or raid marker");
                    Screenshot.grab(output.toFile(), "integrated-projector.png", client.getMainRenderTarget(), result -> {});
                    var readoutTexts = new java.util.ArrayList<String>();
                    for (var displayEntity : client.level.entitiesForRendering()) {
                        if (displayEntity instanceof Display.TextDisplay display) {
                            String text = display.getText().getString();
                            readoutTexts.add(text);
                            if (text.contains("YOU") && text.contains("RAID") && text.contains("M")) {
                                readoutEntity = display.getId();
                                break;
                            }
                        }
                    }
                    if (!client.player.getMainHandItem().has(DataComponents.CUSTOM_MODEL_DATA)) {
                        throw new IllegalStateException("Held radar has no Polymer model");
                    }
                    if (readoutEntity == 0) throw new IllegalStateException("Raid readout did not appear. Client text displays: " + readoutTexts);
                    int contourSegments = 0;
                    for (int i = 0; i < 176; i++) if (value(state, "c" + i + "l") > 0) contourSegments++;
                    if (contourSegments < 12) throw new IllegalStateException("Missing interpolated terrain contours");
                    if (!(projector.getVehicle() instanceof Display.ItemDisplay carrier)) {
                        throw new IllegalStateException("Projector is missing its interpolation carrier");
                    }
                    terrainEntity = entity.getId();
                    carrierEntity = carrier.getId();
                    initialPosition = projector.position();
                    Files.writeString(output.resolve("projector-data.txt"), "known=" + known + " elevation_bands=" + bands.size() + " contours=" + contourSegments
                            + " raid_x=" + value(state, "raid_x") + " raid_z=" + value(state, "raid_z"));
                    rightClick(client);
                    stage = 7;
                    delay = 12;
                    return;
                }
                if (++captureAttempts >= 100) {
                    Screenshot.grab(output.toFile(), "failure-projector.png", client.getMainRenderTarget(), result -> {});
                    throw new IllegalStateException("Server projector did not appear. Camera positioned=" + positionedForCapture);
                }
            }
            case 7 -> {
                if (!(client.player.containerMenu instanceof ChestMenu)) throw new IllegalStateException("Projector intercepted right-click");
                client.player.closeContainer();
                Files.writeString(output.resolve("menu-verified"), "Radar menu opened with projector visible");
                stage = 3;
            }
            case 3 -> {
                if (!Files.exists(output.resolve("follow-turn"))) return;
                client.mouseHandler.releaseMouse();
                if (Math.abs(Mth.wrapDegrees(client.player.getYRot() + 90)) > 1 || Math.abs(client.player.getXRot() - 30) > 1) return;
                if (!(client.level.getEntity(terrainEntity) instanceof GenericBedrockEntity projector)
                        || !(client.level.getEntity(readoutEntity) instanceof Display.TextDisplay readout)
                        || projector.getVehicle() != client.level.getEntity(carrierEntity)) {
                    throw new IllegalStateException("Projector or readout disappeared while following");
                }
                Vec3 eye = client.player.getEyePosition();
                double horizontal = Math.cos(Math.toRadians(30));
                Vec3 expected = eye.add(Vec3.directionFromRotation(30, -90).scale(1.66))
                        .add(0, -0.35 * horizontal, 0);
                Vec3 expectedReadout = expected.subtract(Vec3.directionFromRotation(0, -90).scale(0.60 * horizontal))
                        .subtract(Vec3.directionFromRotation(30, -90).scale(0.176)).add(0, 0.15128, 0);
                if (projector.position().distanceTo(expected) > 0.16 || readout.position().distanceTo(expectedReadout) > 0.16) return;
                if (projector.position().distanceTo(initialPosition) < 4) throw new IllegalStateException("Projector did not follow over distance");
                if (!(projector.getDelegate() instanceof PosableState state) || value(state, "survey_heading") != -90
                        || value(state, "survey_tilt") != -12) return;
                stage = 9;
                delay = 12;
            }
            case 9 -> {
                Screenshot.grab(output.toFile(), "follow-projector.png", client.getMainRenderTarget(), result -> {});
                Files.writeString(output.resolve("follow-verified"), "The same model, carrier and readout followed a 90-degree turn and six-block movement");
                select(client, 1);
                stage = 10;
                delay = 18;
            }
            case 10 -> {
                if (client.level.getEntity(terrainEntity) != null || client.level.getEntity(readoutEntity) != null
                        || client.level.getEntity(carrierEntity) != null) {
                    throw new IllegalStateException("Projector entities remained after switching items");
                }
                select(client, 0);
                stage = 11;
                delay = 4;
            }
            case 11 -> {
                rightClick(client);
                stage = 4;
                delay = 12;
            }
            case 4 -> {
                if (!(client.player.containerMenu instanceof ChestMenu)) throw new IllegalStateException("Radar menu did not reopen");
                click(client, 22);
                stage = 5;
                delay = 35;
            }
            case 5 -> {
                if (!client.player.getMainHandItem().is(Items.FILLED_MAP)) throw new IllegalStateException("Flat map did not open");
                Files.writeString(output.resolve("integrated-result.txt"), "PASS: resource pack, menu, live terrain and raid, readout, right-click with projector visible, camera turn and movement, item-switch cleanup, flat map");
                client.stop();
                stage = 6;
            }
        }
    }

    private static double value(PosableState state, String name) {
        return state.getRuntime().execute(MoLang.parse("return v." + name + ";")).asDouble();
    }

    private static void select(Minecraft client, int slot) {
        client.player.getInventory().selected = slot;
        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static void rightClick(Minecraft client) {
        ((UseItemInvoker) client).radarExperiment$startUseItem();
    }

    private static void click(Minecraft client, int slot) {
        client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, slot, 0, ClickType.PICKUP, client.player);
    }
}
