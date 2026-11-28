package probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Screenshot and frame-time instrumentation. Cobblemon draws all model geometry. */
public final class CaptureProbe implements ClientModInitializer {
    private int stage = -1;
    private int frames;
    private long previous;
    private final long[] times = new long[120];

    @Override public void onInitializeClient() {
        Path output = Path.of(System.getProperty("radar.prototype.output", "."));
        if (Boolean.getBoolean("radar.integrated")) {
            new IntegratedCapture(output).register();
            return;
        }
        WorldRenderEvents.END.register(context -> {
            var client = Minecraft.getInstance();
            if (client.player == null || !Files.exists(output.resolve("stage"))) return;
            try {
                int requested = Integer.parseInt(Files.readString(output.resolve("stage")).trim());
                long now = System.nanoTime();
                if (requested != stage) {
                    stage = requested;
                    frames = 0;
                    previous = now;
                    return;
                }
                if (frames < 30) client.getToasts().clear();
                if (frames >= 30 && frames < 150) times[frames - 30] = now - previous;
                previous = now;
                if (++frames != 150) return;
                Arrays.sort(times);
                Files.writeString(output.resolve("frame-" + stage + ".txt"), "p50_ms=" + times[60] / 1e6 + " p95_ms=" + times[113] / 1e6);
                for (var entity : client.level.entitiesForRendering()) {
                    if (entity instanceof com.cobblemon.mod.common.entity.generic.GenericBedrockEntity generic
                            && generic.getCategory().getNamespace().equals("radarprototype")
                            && generic.getDelegate() instanceof com.cobblemon.mod.common.client.render.models.blockbench.PosableState state
                            && state.getCurrentModel() != null) {
                        var model = state.getCurrentModel();
                        var parts = model.getRelevantPartsByName();
                        Files.writeString(output.resolve("bones-" + stage + ".txt"), "defaults=" + model.getDefaultPositions().size()
                            + " resets=" + model.getTransformedParts().length + " col119=" + parts.get("column_119").yScale
                            + " col120=" + parts.get("column_120").yScale + " col135=" + parts.get("column_135").yScale);
                    }
                }
                int capture = stage;
                client.execute(() -> Screenshot.grab(output.toFile(), "stage-" + capture + ".png", client.getMainRenderTarget(), result -> {}));
            } catch (java.io.IOException | NumberFormatException ignored) {
                // The stage file can be briefly empty while the harness rewrites it.
            }
        });
    }
}
