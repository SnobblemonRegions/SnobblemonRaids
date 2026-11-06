package net.snobblemon.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Vector3f;

/**
 * How one cosmetic is drawn, as the server describes it. Models are never loaded by this mod: the server's resource pack
 * already maps a carrier item + custom model data to each model, and the item renderer resolves it like any item model.
 * The stacks built here exist only inside render code; they are not in any inventory, slot or entity.
 */
public final class CosmeticSpec {
    public enum Anchor { HEAD, BODY, ARM_L, ARM_R, LEG_L, LEG_R }

    public record Part(Anchor anchor, ItemStack stack, boolean glow, float pulseScale, int pulseTicks, float bobAmount, int bobTicks) {}

    public record Hat(int ticks, float bob, float pulse, int pulseTicks, int frameTicks, ItemStack spin, List<ItemStack> frames) {}

    public record Wings(ItemStack left, ItemStack right, ItemStack base, Vector3f hinge, float swing, float lift, int ticks, float idle,
                        Vector3f dispT, Vector3f dispS, Vector3f dispR) {}

    public final String id;
    public final String type;
    public final boolean headMount;
    public final float scale;
    public final float rotY;
    public final float offX, offY, offZ;
    public final ItemStack model;
    public final Hat hat;
    public final Wings wings;
    public final List<Part> parts = new ArrayList<>();

    public CosmeticSpec(JsonObject o) {
        this.id = o.get("id").getAsString();
        this.type = o.get("type").getAsString();
        this.headMount = o.has("head_mount") && o.get("head_mount").getAsBoolean();
        boolean glint = o.has("glint") && o.get("glint").getAsBoolean();
        this.scale = o.has("scale") ? o.get("scale").getAsFloat() : 1f;
        this.rotY = o.has("rot_y") ? o.get("rot_y").getAsFloat() : 0f;
        JsonArray off = o.has("offset") ? o.getAsJsonArray("offset") : null;
        this.offX = off == null ? 0 : off.get(0).getAsFloat();
        this.offY = off == null ? 0 : off.get(1).getAsFloat();
        this.offZ = off == null ? 0 : off.get(2).getAsFloat();
        this.model = o.has("model") ? stack(o.getAsJsonObject("model"), glint) : ItemStack.EMPTY;

        if (o.has("hat_anim")) {
            JsonObject h = o.getAsJsonObject("hat_anim");
            List<ItemStack> frames = new ArrayList<>();
            for (JsonElement f : h.getAsJsonArray("frames")) frames.add(stack(f.getAsJsonObject(), glint));
            this.hat = new Hat(h.get("ticks").getAsInt(), h.get("bob").getAsFloat(), h.get("pulse").getAsFloat(), h.get("pulse_ticks").getAsInt(),
                    Math.max(1, h.get("frame_ticks").getAsInt()), h.has("spin") ? stack(h.getAsJsonObject("spin"), glint) : null, frames);
        } else {
            this.hat = null;
        }

        if (o.has("wings")) {
            JsonObject w = o.getAsJsonObject("wings");
            this.wings = new Wings(stack(w.getAsJsonObject("left"), glint), stack(w.getAsJsonObject("right"), glint),
                    w.has("base") ? stack(w.getAsJsonObject("base"), glint) : null, vec(w, "hinge"),
                    w.get("swing").getAsFloat(), w.get("lift").getAsFloat(), w.get("ticks").getAsInt(), w.get("idle").getAsFloat(),
                    vec(w, "disp_t"), vec(w, "disp_s"), vec(w, "disp_r"));
        } else {
            this.wings = null;
        }

        if (o.has("parts")) {
            for (JsonElement e : o.getAsJsonArray("parts")) {
                JsonObject p = e.getAsJsonObject();
                parts.add(new Part(Anchor.valueOf(p.get("anchor").getAsString()), stack(p.getAsJsonObject("model"), glint), p.get("glow").getAsBoolean(),
                        p.get("pulse_scale").getAsFloat(), Math.max(4, p.get("pulse_ticks").getAsInt()),
                        p.get("bob_amount").getAsFloat(), Math.max(4, p.get("bob_ticks").getAsInt())));
            }
        }
    }

    private static Vector3f vec(JsonObject o, String key) {
        JsonArray a = o.getAsJsonArray(key);
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static ItemStack stack(JsonObject m, boolean glint) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(m.get("item").getAsString()));
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(m.get("cmd").getAsInt()));
        if (glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    public boolean isArmor() {
        return type.startsWith("armor_");
    }
}
