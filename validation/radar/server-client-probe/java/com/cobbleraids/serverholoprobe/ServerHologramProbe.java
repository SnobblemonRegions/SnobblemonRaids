package com.cobbleraids.serverholoprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

/**
 * Development-only visual probe for the SERVER-SIDE radar hologram. It runs in a client with no CobbleRaids code at all (the companion would switch the
 * server hologram off), counts the display entities the server sent and whether they ride something, and takes screenshots. Excluded from every shipped JAR.
 */
public final class ServerHologramProbe implements ClientModInitializer {
    /** {@code -Dcobbleraids.radar.smoke.raidio=true}: the radar is Raidio, which draws no hologram, so the probe only holds it and photographs the item. */
    private static final boolean RAIDIO = Boolean.getBoolean("cobbleraids.radar.smoke.raidio");
    private int stage, delay, ticks;
    private Path output;

    @Override
    public void onInitializeClient() {
        output = Path.of(System.getProperty("cobbleraids.radar.smoke.output"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                tick(client);
            } catch (Throwable error) {
                try {
                    Files.writeString(output.resolve("graphical-failure.txt"), error.toString());
                } catch (Exception ignored) {
                }
                error.printStackTrace();
                client.stop();
            }
        });
    }

    private record Count(int blocks, int riding, int texts) {}

    private static Count count(Minecraft client) {
        int blocks = 0, riding = 0, texts = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Display.BlockDisplay) {
                blocks++;
                if (entity.getVehicle() != null) riding++;
            } else if (entity instanceof Display.TextDisplay) {
                texts++;
            }
        }
        return new Count(blocks, riding, texts);
    }

    private void tick(Minecraft client) throws Exception {
        if (++ticks > 20 * 240) throw new IllegalStateException("Server hologram probe timed out at stage " + stage);
        if (client.player == null || client.level == null || client.gameMode == null) return;
        client.getToasts().clear();
        if (delay-- > 0) return;
        switch (stage) {
            case 0 -> {
                if (!Files.exists(output.resolve("fixture-ready"))) return;
                if (client.player.getInventory().selected != 0) {
                    client.player.getInventory().selected = 0;
                    client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
                }
                if (RAIDIO) {
                    if (!client.player.getMainHandItem().getHoverName().getString().equals("Raidio")) return;
                    client.player.setYRot(180);
                    client.player.setXRot(15);
                    stage = 100;
                    delay = 80;   // the resource pack has to arrive and the client reload before the item draws its model
                    return;
                }
                if (!client.player.getMainHandItem().getHoverName().getString().equals("Raid Radar")) return;
                client.player.setYRot(180);
                client.player.setXRot(35);
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                stage = 10;
                delay = 20;
            }
            case 10 -> {
                if (!(client.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)) throw new IllegalStateException("Raid status menu did not open");
                screenshot(client, "radar-status-menu.png");
                stage = 11;
                delay = 20;
            }
            case 11 -> {
                // 20 is the open/close control in the framed menu layout.
                client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, 20, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, client.player);
                stage = 1;
                delay = 300;
            }
            case 1 -> {
                Count found = count(client);
                if (found.blocks() < 10) {
                    if (ticks > 20 * 200) throw new IllegalStateException("No server hologram arrived: block displays=" + found.blocks());
                    return;
                }
                Files.writeString(output.resolve("server-hologram-counts.json"), String.format(java.util.Locale.ROOT,
                        "{\"blockDisplays\":%d,\"riding\":%d,\"textDisplays\":%d}", found.blocks(), found.riding(), found.texts()));
                if (found.riding() < found.blocks()) throw new IllegalStateException("Columns are not riding the root: " + found);
                // Look down at it, as a player reading the radar does, then capture with the map still in the main hand.
                client.player.setXRot(35);
                stage = 20;
                delay = 40;
            }
            case 20 -> {
                screenshot(client, "server-hologram-front.png");
                stage = 2;
                delay = 20;
            }
            case 2 -> {
                // The held map covers the middle of the screen, so the radar goes to the offhand and the hologram is seen unobstructed.
                client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
                stage = 3;
                delay = 60;
            }
            case 3 -> {
                client.player.setYRot(180);
                client.player.setXRot(20);
                stage = 4;
                delay = 60;
            }
            case 4 -> {
                screenshot(client, "server-hologram-offhand-front.png");
                client.player.setYRot(270);
                stage = 5;
                delay = 60;
            }
            case 5 -> {
                screenshot(client, "server-hologram-offhand-turned.png");
                stage = 6;
                delay = 30;
            }
            case 6 -> {
                // Choose the flat map in the menu (slot 13): the hologram must go away, and the map take its place.
                client.gameMode.useItem(client.player, InteractionHand.OFF_HAND);
                stage = 7;
                delay = 30;
            }
            case 7 -> {
                if (!(client.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)) throw new IllegalStateException("Radar menu did not open for the offhand radar");
                client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, 13, 0,
                        net.minecraft.world.inventory.ClickType.PICKUP, client.player);
                stage = 8;
                delay = 40;
            }
            case 8 -> {
                client.player.closeContainer();
                client.player.setXRot(20);
                stage = 9;
                delay = 60;
            }
            case 9 -> {
                Count left = count(client);
                if (left.blocks() != 0) throw new IllegalStateException("The hologram is still shown in flat-map mode: " + left);
                screenshot(client, "flat-map-mode.png");
                stage = 12;
                delay = 30;
            }
            case 100 -> {
                screenshot(client, "raidio-hand-level.png");
                client.player.setXRot(50);
                stage = 101;
                delay = 30;
            }
            case 101 -> {
                screenshot(client, "raidio-hand-looking-down.png");
                client.player.setXRot(15);
                client.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
                stage = 102;
                delay = 30;
            }
            case 102 -> {
                screenshot(client, "raidio-inventory.png");
                client.setScreen(null);
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                stage = 103;
                delay = 40;
            }
            case 103 -> {
                screenshot(client, "raidio-third-person-back.png");
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                stage = 104;
                delay = 40;
            }
            case 104 -> {
                screenshot(client, "raidio-third-person-front.png");
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                stage = 105;
                delay = 40;
            }
            case 105 -> {
                for (String name : new String[] {"raidio-hand-level.png", "raidio-hand-looking-down.png", "raidio-inventory.png",
                        "raidio-third-person-back.png", "raidio-third-person-front.png"}) {
                    if (!Files.exists(output.resolve("screenshots").resolve(name))) return;
                }
                Files.writeString(output.resolve("graphical-result.json"), "{\"passed\":true}");
                stage = 13;
                client.stop();
            }
            case 12 -> {
                for (String name : new String[] {"radar-status-menu.png", "server-hologram-front.png", "server-hologram-offhand-front.png",
                        "server-hologram-offhand-turned.png", "flat-map-mode.png"}) {
                    if (!Files.exists(output.resolve("screenshots").resolve(name))) return;
                }
                Files.writeString(output.resolve("graphical-result.json"), "{\"passed\":true}");
                stage = 13;
                client.stop();
            }
        }
    }

    private void screenshot(Minecraft client, String name) {
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), message -> System.out.println("RADAR SCREENSHOT: " + message.getString()));
    }
}
