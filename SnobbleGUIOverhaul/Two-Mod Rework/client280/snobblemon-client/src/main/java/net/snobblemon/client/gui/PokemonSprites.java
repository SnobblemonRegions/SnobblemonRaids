package net.snobblemon.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.snobblemon.proto.SnobbleProto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Small Pokemon pictures sent by the server on request, turned into textures once and kept for the session. */
public final class PokemonSprites {
    private PokemonSprites() { }

    public record Tex(ResourceLocation id, int w, int h) { }

    private static final Map<String, Tex> READY = new HashMap<>();
    private static final Set<String> ASKED = new HashSet<>();      // asked once; an empty answer is remembered as "no picture"
    private static final Set<String> NONE = new HashSet<>();

    /** The picture if it is here; otherwise asks the server (once) and returns null for now. */
    public static Tex get(String key) {
        Tex t = READY.get(key);
        if (t != null || NONE.contains(key)) return t;
        if (ASKED.add(key) && ClientPlayNetworking.canSend(SnobbleProto.SpriteReq.TYPE)) ClientPlayNetworking.send(new SnobbleProto.SpriteReq(key));
        else if (!ClientPlayNetworking.canSend(SnobbleProto.SpriteReq.TYPE)) ASKED.remove(key);
        return null;
    }

    public static void received(String key, byte[] png) {
        if (png.length == 0) { NONE.add(key); return; }
        try (NativeImage img = NativeImage.read(new ByteArrayInputStream(png))) {
            if (img.getWidth() > 512 || img.getHeight() > 512) { NONE.add(key); return; }
            NativeImage copy = new NativeImage(img.getWidth(), img.getHeight(), false);
            copy.copyFrom(img);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("snobblemon_client", "sprite/" + key.replace(':', '_').replace("+", "_plus_"));
            DynamicTexture tex = new DynamicTexture(copy);
            // portraits ("p:") are rendered pictures: scale them smoothly like the web page did; pixel sprites stay sharp
            if (key.startsWith("p:") || key.startsWith("w:") || key.startsWith("g:")) tex.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            READY.put(key, new Tex(id, copy.getWidth(), copy.getHeight()));
        } catch (IOException | RuntimeException e) {
            NONE.add(key);
        }
    }

    public static void clear() { READY.clear(); ASKED.clear(); NONE.clear(); }
}
